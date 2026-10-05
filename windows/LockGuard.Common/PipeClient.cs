using System.IO.Pipes;
using System.Text.Json;

namespace LockGuard.Common;

/// <summary>Thin client for the Setup app to talk to the running service. One request, one response, one connection.</summary>
public static class PipeClient
{
    public static async Task<PipeResponse> SendAsync(PipeRequest request, int timeoutMs = 4000)
    {
        using var pipe = new NamedPipeClientStream(".", Paths.PipeName, PipeDirection.InOut, PipeOptions.Asynchronous);
        using var cts = new CancellationTokenSource(timeoutMs);
        try
        {
            await pipe.ConnectAsync(timeoutMs, cts.Token);
        }
        catch (Exception e)
        {
            return new PipeResponse { Ok = false, Message = "Could not reach the LockGuard service: " + e.Message };
        }

        using var writer = new StreamWriter(pipe, leaveOpen: true) { AutoFlush = true };
        using var reader = new StreamReader(pipe, leaveOpen: true);

        await writer.WriteLineAsync(JsonSerializer.Serialize(request));
        var line = await reader.ReadLineAsync(cts.Token);
        if (line is null) return new PipeResponse { Ok = false, Message = "No response from the service." };

        try
        {
            return JsonSerializer.Deserialize<PipeResponse>(line) ?? new PipeResponse { Ok = false, Message = "Bad response." };
        }
        catch
        {
            return new PipeResponse { Ok = false, Message = "Bad response." };
        }
    }
}
