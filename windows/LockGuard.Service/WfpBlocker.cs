using System.Runtime.InteropServices;
using System.Runtime.Versioning;

namespace LockGuard.Service;

/// <summary>
/// "Only through the filter", enforced in the Windows Filtering Platform: the layer of Windows
/// that every firewall (Windows Firewall, Avast, Norton...) is built on. A Windows Firewall rule
/// is ignored once another firewall takes over; a block added here is not -- across the platform,
/// a block wins. Blocks every outgoing lookup to port 53 (plain) and 853 (encrypted) that is not
/// to this computer itself (the filter on 127.0.0.1 / ::1). Persistent: stays through restarts
/// until removed. Values from Windows' fwpmu.h / fwpmtypes.h / fwptypes.h (checked against the
/// mingw-w64 headers and WireGuard for Windows' definitions).
/// </summary>
[SupportedOSPlatform("windows")]
public static class WfpBlocker
{
    private static readonly Guid SubLayer = new("6a1f0c5e-7b2d-4c11-9a8e-4c6f636b6701");
    private static readonly Guid LayerConnectV4 = new("c38d57d1-05a7-4c33-904f-7fbceee60e82");
    private static readonly Guid LayerConnectV6 = new("4a72393b-319f-44bc-84c3-ba54dcb3b6b4");
    private static readonly Guid CondRemotePort = new("c35a604d-d22b-4e1a-91b4-68f674ee674b");
    private static readonly Guid CondFlags = new("632ce23b-5167-435c-86d7-e903684aa80c");

    private const uint RPC_C_AUTHN_WINNT = 10;
    private const uint FWP_EMPTY = 0, FWP_UINT16 = 2, FWP_UINT32 = 3;
    private const uint FWP_MATCH_EQUAL = 0, FWP_MATCH_FLAGS_NONE_SET = 8;
    private const uint FWP_CONDITION_FLAG_IS_LOOPBACK = 0x1;
    private const uint FWP_ACTION_BLOCK = 0x1 | 0x1000;
    private const uint FWPM_FILTER_FLAG_PERSISTENT = 0x1;
    private const ushort FWPM_SUBLAYER_FLAG_PERSISTENT = 0x1;
    private const uint FWP_E_ALREADY_EXISTS = 0x80320009, FWP_E_FILTER_NOT_FOUND = 0x80320003, FWP_E_SUBLAYER_NOT_FOUND = 0x80320007;

    [StructLayout(LayoutKind.Sequential)] private struct DisplayData { public IntPtr name; public IntPtr description; }
    [StructLayout(LayoutKind.Sequential)] private struct ByteBlob { public uint size; public IntPtr data; }
    [StructLayout(LayoutKind.Sequential)] private struct Value { public uint type; public ulong value; }
    [StructLayout(LayoutKind.Sequential)] private struct Condition { public Guid fieldKey; public uint matchType; public Value conditionValue; }
    [StructLayout(LayoutKind.Sequential)] private struct Action { public uint type; public Guid filterType; }

    [StructLayout(LayoutKind.Sequential)]
    private struct SubLayer0
    {
        public Guid subLayerKey;
        public DisplayData displayData;
        public ushort flags;
        public IntPtr providerKey;
        public ByteBlob providerData;
        public ushort weight;
    }

    [StructLayout(LayoutKind.Sequential)]
    private struct Filter0
    {
        public Guid filterKey;
        public DisplayData displayData;
        public uint flags;
        public IntPtr providerKey;
        public ByteBlob providerData;
        public Guid layerKey;
        public Guid subLayerKey;
        public Value weight;
        public uint numFilterConditions;
        public IntPtr filterCondition;
        public Action action;
        public ulong rawContext0, rawContext1; // union { UINT64 rawContext; GUID providerContextKey; }
        public IntPtr reserved;
        public ulong filterId;
        public Value effectiveWeight;
    }

    [DllImport("fwpuclnt.dll", CharSet = CharSet.Unicode)] private static extern uint FwpmEngineOpen0(string? serverName, uint authnService, IntPtr authIdentity, IntPtr session, out IntPtr engine);
    [DllImport("fwpuclnt.dll")] private static extern uint FwpmEngineClose0(IntPtr engine);
    [DllImport("fwpuclnt.dll")] private static extern uint FwpmSubLayerAdd0(IntPtr engine, ref SubLayer0 subLayer, IntPtr sd);
    [DllImport("fwpuclnt.dll")] private static extern uint FwpmSubLayerDeleteByKey0(IntPtr engine, ref Guid key);
    [DllImport("fwpuclnt.dll")] private static extern uint FwpmFilterAdd0(IntPtr engine, ref Filter0 filter, IntPtr sd, out ulong id);
    [DllImport("fwpuclnt.dll")] private static extern uint FwpmFilterDeleteByKey0(IntPtr engine, ref Guid key);
    [DllImport("fwpuclnt.dll")] private static extern uint FwpmFilterDeleteById0(IntPtr engine, ulong id);
    [DllImport("fwpuclnt.dll", CharSet = CharSet.Unicode)] private static extern uint FwpmGetAppIdFromFileName0(string fileName, out IntPtr appId);
    [DllImport("fwpuclnt.dll")] private static extern void FwpmFreeMemory0(ref IntPtr p);
    private static readonly Guid CondAppId = new("d78e1e87-8644-4ea5-9437-d809ecefc971"); // FWPM_CONDITION_ALE_APP_ID
    private const uint FWP_BYTE_BLOB_TYPE = 12;
    private static readonly List<ulong> AppBlockIds = new();
    private static string AppBlockFile => Path.Combine(LockGuard.Common.Paths.RootDir, "app-blocks.txt");

    public static string? LastError { get; private set; }

    /// <summary>Our filters: (layer, port), each with a fixed key so they can be found and removed again.</summary>
    private static IEnumerable<(Guid key, Guid layer, ushort port)> Filters()
    {
        int i = 0;
        foreach (var layer in new[] { LayerConnectV4, LayerConnectV6 })
            foreach (ushort port in new ushort[] { 53, 853 })
                yield return (new Guid($"6a1f0c5e-7b2d-4c11-9a8e-4c6f636b67{(0x10 + i++):x2}"), layer, port);
    }

    public static bool Set(bool on)
    {
        IntPtr engine = IntPtr.Zero;
        var name = Marshal.StringToHGlobalUni("LockGuard web filter");
        var desc = Marshal.StringToHGlobalUni("Lookups only through LockGuard's filter on this computer.");
        IntPtr conds = IntPtr.Zero;
        try
        {
            uint r = FwpmEngineOpen0(null, RPC_C_AUTHN_WINNT, IntPtr.Zero, IntPtr.Zero, out engine);
            if (r != 0) throw new InvalidOperationException($"FwpmEngineOpen0 0x{r:x8}");
            if (!on)
            {
                foreach (var f in Filters())
                {
                    var k = f.key;
                    r = FwpmFilterDeleteByKey0(engine, ref k);
                    if (r != 0 && r != FWP_E_FILTER_NOT_FOUND) throw new InvalidOperationException($"FwpmFilterDeleteByKey0 0x{r:x8}");
                }
                var s = SubLayer;
                r = FwpmSubLayerDeleteByKey0(engine, ref s);
                if (r != 0 && r != FWP_E_SUBLAYER_NOT_FOUND) throw new InvalidOperationException($"FwpmSubLayerDeleteByKey0 0x{r:x8}");
                LastError = null;
                return true;
            }
            var sub = new SubLayer0 { subLayerKey = SubLayer, displayData = new DisplayData { name = name, description = desc }, flags = FWPM_SUBLAYER_FLAG_PERSISTENT, weight = 0x7fff };
            r = FwpmSubLayerAdd0(engine, ref sub, IntPtr.Zero);
            if (r != 0 && r != FWP_E_ALREADY_EXISTS) throw new InvalidOperationException($"FwpmSubLayerAdd0 0x{r:x8}");
            int condSize = Marshal.SizeOf<Condition>();
            conds = Marshal.AllocHGlobal(condSize * 2);
            foreach (var f in Filters())
            {
                Marshal.StructureToPtr(new Condition { fieldKey = CondRemotePort, matchType = FWP_MATCH_EQUAL, conditionValue = new Value { type = FWP_UINT16, value = f.port } }, conds, false);
                Marshal.StructureToPtr(new Condition { fieldKey = CondFlags, matchType = FWP_MATCH_FLAGS_NONE_SET, conditionValue = new Value { type = FWP_UINT32, value = FWP_CONDITION_FLAG_IS_LOOPBACK } }, conds + condSize, false);
                var filter = new Filter0
                {
                    filterKey = f.key,
                    displayData = new DisplayData { name = name, description = desc },
                    flags = FWPM_FILTER_FLAG_PERSISTENT,
                    layerKey = f.layer,
                    subLayerKey = SubLayer,
                    weight = new Value { type = FWP_EMPTY },
                    numFilterConditions = 2,
                    filterCondition = conds,
                    action = new Action { type = FWP_ACTION_BLOCK },
                };
                r = FwpmFilterAdd0(engine, ref filter, IntPtr.Zero, out _);
                if (r != 0 && r != FWP_E_ALREADY_EXISTS) throw new InvalidOperationException($"FwpmFilterAdd0 0x{r:x8}");
            }
            LastError = null;
            return true;
        }
        catch (Exception e)
        {
            LastError = "Could not set the low-level lookup block: " + e.Message;
            return false;
        }
        finally
        {
            if (conds != IntPtr.Zero) Marshal.FreeHGlobal(conds);
            Marshal.FreeHGlobal(name);
            Marshal.FreeHGlobal(desc);
            if (engine != IntPtr.Zero) FwpmEngineClose0(engine);
        }
    }

    /// <summary>
    /// "No internet" for single programs: a block for each program's outgoing connections (IPv4 and
    /// IPv6), in the same layer as the lookup block, so another firewall (Avast) cannot override it.
    /// Not persistent: gone after a restart until the service puts them back, so they can never be
    /// left behind. The filter ids are remembered (also in a file) to take them away again.
    /// </summary>
    public static bool SetAppBlocks(List<string> paths)
    {
        IntPtr engine = IntPtr.Zero;
        var name = Marshal.StringToHGlobalUni("LockGuard: no internet for a program");
        var desc = Marshal.StringToHGlobalUni("Set on the dashboard (Programs).");
        IntPtr cond = IntPtr.Zero;
        try
        {
            uint r = FwpmEngineOpen0(null, RPC_C_AUTHN_WINNT, IntPtr.Zero, IntPtr.Zero, out engine);
            if (r != 0) throw new InvalidOperationException($"FwpmEngineOpen0 0x{r:x8}");
            lock (AppBlockIds)
            {
                try { if (File.Exists(AppBlockFile)) foreach (var l in File.ReadAllLines(AppBlockFile)) if (ulong.TryParse(l, out var id) && !AppBlockIds.Contains(id)) AppBlockIds.Add(id); } catch { }
                foreach (var id in AppBlockIds) FwpmFilterDeleteById0(engine, id);
                AppBlockIds.Clear();
                if (paths.Count > 0)
                {
                    var sub = new SubLayer0 { subLayerKey = SubLayer, displayData = new DisplayData { name = name, description = desc }, flags = FWPM_SUBLAYER_FLAG_PERSISTENT, weight = 0x7fff };
                    r = FwpmSubLayerAdd0(engine, ref sub, IntPtr.Zero);
                    if (r != 0 && r != FWP_E_ALREADY_EXISTS) throw new InvalidOperationException($"FwpmSubLayerAdd0 0x{r:x8}");
                }
                cond = Marshal.AllocHGlobal(Marshal.SizeOf<Condition>());
                foreach (var path in paths)
                {
                    if (!File.Exists(path)) continue;
                    if (FwpmGetAppIdFromFileName0(path, out var appId) != 0) continue;
                    try
                    {
                        Marshal.StructureToPtr(new Condition { fieldKey = CondAppId, matchType = FWP_MATCH_EQUAL, conditionValue = new Value { type = FWP_BYTE_BLOB_TYPE, value = (ulong)appId.ToInt64() } }, cond, false);
                        foreach (var layer in new[] { LayerConnectV4, LayerConnectV6 })
                        {
                            var filter = new Filter0
                            {
                                filterKey = Guid.NewGuid(),
                                displayData = new DisplayData { name = name, description = desc },
                                flags = 0,
                                layerKey = layer,
                                subLayerKey = SubLayer,
                                weight = new Value { type = FWP_EMPTY },
                                numFilterConditions = 1,
                                filterCondition = cond,
                                action = new Action { type = FWP_ACTION_BLOCK },
                            };
                            if (FwpmFilterAdd0(engine, ref filter, IntPtr.Zero, out var id) == 0) AppBlockIds.Add(id);
                        }
                    }
                    finally { FwpmFreeMemory0(ref appId); }
                }
                try { Directory.CreateDirectory(LockGuard.Common.Paths.RootDir); File.WriteAllLines(AppBlockFile, AppBlockIds.Select(x => x.ToString())); } catch { }
            }
            return true;
        }
        catch (Exception e)
        {
            LastError = "Could not set the program internet blocks: " + e.Message;
            return false;
        }
        finally
        {
            if (cond != IntPtr.Zero) Marshal.FreeHGlobal(cond);
            Marshal.FreeHGlobal(name);
            Marshal.FreeHGlobal(desc);
            if (engine != IntPtr.Zero) FwpmEngineClose0(engine);
        }
    }

    /// <summary>Struct sizes, for a check against Windows' own (x64: sublayer 72, filter 200, condition 40).</summary>
    public static (int sub, int filter, int cond) Sizes() => (Marshal.SizeOf<SubLayer0>(), Marshal.SizeOf<Filter0>(), Marshal.SizeOf<Condition>());
}
