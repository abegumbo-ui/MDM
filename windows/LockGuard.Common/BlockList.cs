using System.Buffers.Binary;
using System.Security.Cryptography;
using System.Text;

namespace LockGuard.Common;

/// <summary>
/// One category list ("MDB1" file from the dashboard's "blocklists" release, built by
/// tools/build-blocklists.mjs): "MDB1", a big-endian count, then that many sorted unsigned 64-bit
/// numbers, each the first 8 bytes of the SHA-256 of a domain. Same format and lookup as the
/// phones (android/sitepolicy BlockList.java).
/// </summary>
public sealed class BlockList
{
    private const int Header = 8;
    private readonly byte[] _data;
    public int Count { get; }

    private BlockList(byte[] data, int count)
    {
        _data = data;
        Count = count;
    }

    public static BlockList FromBytes(byte[] b)
    {
        if (b.Length < Header || b[0] != 'M' || b[1] != 'D' || b[2] != 'B' || b[3] != '1') throw new InvalidDataException("not a blocklist file");
        int n = BinaryPrimitives.ReadInt32BigEndian(b.AsSpan(4));
        if (n < 0 || Header + 8L * n != b.Length) throw new InvalidDataException("blocklist is incomplete");
        return new BlockList(b, n);
    }

    public static ulong Hash(string domain)
    {
        var d = SHA256.HashData(Encoding.UTF8.GetBytes(domain));
        return BinaryPrimitives.ReadUInt64BigEndian(d);
    }

    private bool ContainsHash(ulong h)
    {
        int lo = 0, hi = Count - 1;
        while (lo <= hi)
        {
            int mid = (int)((uint)(lo + hi) >> 1);
            ulong v = BinaryPrimitives.ReadUInt64BigEndian(_data.AsSpan(Header + 8 * mid));
            if (v == h) return true;
            if (v < h) lo = mid + 1; else hi = mid - 1;
        }
        return false;
    }

    /// <summary>The host, or any parent domain of it (never a bare "com"), is on the list.</summary>
    public bool Blocks(string? host)
    {
        if (host == null) return false;
        var h = host.ToLowerInvariant().TrimEnd('.');
        if (h.StartsWith("www.")) h = h[4..];
        while (true)
        {
            if (ContainsHash(Hash(h))) return true;
            int dot = h.IndexOf('.');
            if (dot < 0) return false;
            h = h[(dot + 1)..];
            if (!h.Contains('.')) return false;
        }
    }

    /// <summary>Builds a list from domains (for tests).</summary>
    public static BlockList Build(IEnumerable<string> domains)
    {
        var hashes = domains.Select(Hash).Distinct().OrderBy(x => x).ToArray();
        var b = new byte[Header + 8 * hashes.Length];
        b[0] = (byte)'M'; b[1] = (byte)'D'; b[2] = (byte)'B'; b[3] = (byte)'1';
        BinaryPrimitives.WriteInt32BigEndian(b.AsSpan(4), hashes.Length);
        for (int i = 0; i < hashes.Length; i++) BinaryPrimitives.WriteUInt64BigEndian(b.AsSpan(Header + 8 * i), hashes[i]);
        return FromBytes(b);
    }
}
