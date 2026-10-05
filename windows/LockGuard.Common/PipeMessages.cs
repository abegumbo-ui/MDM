using System.Text.Json.Serialization;

namespace LockGuard.Common;

/// <summary>
/// The only way the Setup app changes anything: a request over the named pipe, authenticated by
/// the current code every single time (not just once at the start of a session). The service is
/// the only thing that ever writes config.json or touches the firewall rules -- Setup only ever
/// asks it to.
/// </summary>
public sealed class PipeRequest
{
    [JsonPropertyName("action")] public string Action { get; set; } = "";
    [JsonPropertyName("code")] public string Code { get; set; } = "";
    [JsonPropertyName("arg")] public string? Arg { get; set; }
}

public sealed class PipeResponse
{
    [JsonPropertyName("ok")] public bool Ok { get; set; }
    [JsonPropertyName("message")] public string Message { get; set; } = "";
    [JsonPropertyName("enabled")] public bool Enabled { get; set; }
    [JsonPropertyName("isDefaultCode")] public bool IsDefaultCode { get; set; }
    [JsonPropertyName("allowedPrograms")] public List<string> AllowedPrograms { get; set; } = new();
    [JsonPropertyName("firewallOk")] public bool FirewallOk { get; set; } = true;
    [JsonPropertyName("firewallError")] public string? FirewallError { get; set; }
}

public static class PipeActions
{
    public const string GetStatus = "GetStatus";
    public const string VerifyCode = "VerifyCode"; // checks the code only -- no side effects
    public const string SetCode = "SetCode";
    public const string AddProgram = "AddProgram";
    public const string RemoveProgram = "RemoveProgram";
    public const string SetEnabled = "SetEnabled"; // arg: "true" or "false"
    public const string Uninstall = "Uninstall"; // tells the service to remove itself cleanly
}
