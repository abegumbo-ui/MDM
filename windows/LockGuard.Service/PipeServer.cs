using System.IO.Pipes;
using System.Security.AccessControl;
using System.Security.Principal;
using System.Text.Json;
using LockGuard.Common;

namespace LockGuard.Service;

/// <summary>
/// Listens for the Setup app on a local named pipe, one connection at a time. Every request
/// carries the code and is checked fresh -- there is no "logged in" session to steal or leave
/// open. This class decides what's allowed; it never trusts the caller's own claims about
/// anything.
/// </summary>
public sealed class PipeServer
{
    private readonly CodeStore _codeStore = new();
    private readonly Action _onConfigChanged;
    private readonly Action _onUninstallRequested;

    public PipeServer(Action onConfigChanged, Action onUninstallRequested)
    {
        _onConfigChanged = onConfigChanged;
        _onUninstallRequested = onUninstallRequested;
    }

    public async Task RunAsync(CancellationToken ct)
    {
        // The service runs as SYSTEM; without an explicit ACL here, Windows applies the default
        // security descriptor from that token, which doesn't grant the signed-in user's own
        // process (Setup) access to the pipe at all -- not even when it's running elevated. The
        // actual authorization boundary is the per-request code check in Handle(), not the OS
        // pipe ACL, so it's correct to let any local, authenticated user open the pipe here.
        var pipeSecurity = new PipeSecurity();
        pipeSecurity.AddAccessRule(new PipeAccessRule(
            new SecurityIdentifier(WellKnownSidType.AuthenticatedUserSid, null),
            PipeAccessRights.ReadWrite, AccessControlType.Allow));

        while (!ct.IsCancellationRequested)
        {
            using var pipe = NamedPipeServerStreamAcl.Create(Paths.PipeName, PipeDirection.InOut, 1,
                PipeTransmissionMode.Byte, PipeOptions.Asynchronous, 0, 0, pipeSecurity);
            try
            {
                await pipe.WaitForConnectionAsync(ct);
                await HandleOneAsync(pipe);
            }
            catch (OperationCanceledException)
            {
                return;
            }
            catch
            {
                // One bad request should never take the pipe server down; the next connection gets a clean pipe.
            }
        }
    }

    private async Task HandleOneAsync(NamedPipeServerStream pipe)
    {
        using var reader = new StreamReader(pipe, leaveOpen: true);
        using var writer = new StreamWriter(pipe, leaveOpen: true) { AutoFlush = true };

        var line = await reader.ReadLineAsync();
        if (line is null) return;

        PipeRequest? req;
        try
        {
            req = JsonSerializer.Deserialize<PipeRequest>(line);
        }
        catch
        {
            req = null;
        }

        var resp = req is null ? new PipeResponse { Ok = false, Message = "Bad request." } : Handle(req);
        await writer.WriteLineAsync(JsonSerializer.Serialize(resp));
    }

    private PipeResponse Handle(PipeRequest req)
    {
        // GetStatus is read-only and needs no code -- the Setup app has to know whether the
        // bootstrap code is still active before it can know whether to ask for it.
        if (req.Action == PipeActions.GetStatus) return StatusResponse("");

        if (!_codeStore.Verify(req.Code))
            return new PipeResponse { Ok = false, Message = "Wrong code." };

        var config = LockConfig.Load();
        switch (req.Action)
        {
            case PipeActions.VerifyCode:
                return StatusResponse(""); // reaching here already proved the code was right

            case PipeActions.SetCode:
                if (string.IsNullOrWhiteSpace(req.Arg))
                    return new PipeResponse { Ok = false, Message = "Enter a new code." };
                try
                {
                    _codeStore.SetCode(req.Arg);
                }
                catch (Exception e)
                {
                    return new PipeResponse { Ok = false, Message = e.Message };
                }
                return StatusResponse("Code changed.");

            case PipeActions.AddProgram:
                if (string.IsNullOrWhiteSpace(req.Arg)) return new PipeResponse { Ok = false, Message = "No program given." };
                if (!config.AllowedPrograms.Contains(req.Arg, StringComparer.OrdinalIgnoreCase))
                    config.AllowedPrograms.Add(req.Arg);
                config.Save();
                _onConfigChanged();
                return StatusResponse("Allowed.");

            case PipeActions.RemoveProgram:
                config.AllowedPrograms.RemoveAll(p => string.Equals(p, req.Arg, StringComparison.OrdinalIgnoreCase));
                config.Save();
                _onConfigChanged();
                return StatusResponse("Removed.");

            case PipeActions.SetEnabled:
                config.Enabled = req.Arg == "true";
                config.Save();
                _onConfigChanged();
                return StatusResponse(config.Enabled ? "Lockdown on." : "Lockdown off.");

            case PipeActions.Uninstall:
                _onUninstallRequested();
                return new PipeResponse { Ok = true, Message = "Removing..." };

            default:
                return new PipeResponse { Ok = false, Message = "Unknown action." };
        }
    }

    private PipeResponse StatusResponse(string message)
    {
        var config = LockConfig.Load();
        var firewall = FirewallStatus.Load();
        return new PipeResponse
        {
            Ok = true,
            Message = message,
            Enabled = config.Enabled,
            IsDefaultCode = _codeStore.IsDefault(),
            AllowedPrograms = config.AllowedPrograms,
            FirewallOk = firewall.Ok,
            FirewallError = firewall.Error,
        };
    }
}
