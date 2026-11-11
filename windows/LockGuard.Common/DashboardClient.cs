using System.Net.Http.Json;
using System.Text.Json.Serialization;

namespace LockGuard.Common;

/// <summary>
/// Talks to the family dashboard (the same Cloudflare Worker the phones use): registers this
/// computer the first time, then on every check-in reports its state and gets back what to apply.
/// Only used when a dashboard URL is known and it can reach it. The dashboard is treated as
/// authoritative: whatever it says to apply wins over any local change made through Setup, the
/// next time this syncs.
/// </summary>
public sealed class DashboardClient
{
    private readonly string _baseUrl;
    private readonly HttpClient _http = new() { Timeout = TimeSpan.FromSeconds(15) };

    public DashboardClient(string dashboardUrl)
    {
        _baseUrl = dashboardUrl.TrimEnd('/');
    }

    private sealed class DashboardState
    {
        [JsonPropertyName("token")] public string? Token { get; set; }
    }

    private static DashboardState LoadState()
    {
        if (!File.Exists(Paths.DashboardStateFile)) return new DashboardState();
        try
        {
            return System.Text.Json.JsonSerializer.Deserialize<DashboardState>(File.ReadAllText(Paths.DashboardStateFile)) ?? new DashboardState();
        }
        catch
        {
            return new DashboardState();
        }
    }

    private static void SaveState(DashboardState state)
    {
        Directory.CreateDirectory(Paths.RootDir);
        File.WriteAllText(Paths.DashboardStateFile, System.Text.Json.JsonSerializer.Serialize(state));
    }

    private sealed class RegisterResponse
    {
        [JsonPropertyName("token")] public string? Token { get; set; }
    }

    public sealed class SyncResult
    {
        [JsonPropertyName("enabled")] public bool Enabled { get; set; }
        [JsonPropertyName("allowedPrograms")] public List<string> AllowedPrograms { get; set; } = new();
        [JsonPropertyName("commands")] public List<DashboardCommand> Commands { get; set; } = new();
        /// <summary>The web filter's settings; null = no web filter on this computer.</summary>
        [JsonPropertyName("filter")] public FilterPolicy? Filter { get; set; }
        [JsonPropertyName("pollSeconds")] public int PollSeconds { get; set; }
        /// <summary>The newest published LockGuard, when newer than the build this computer reported.</summary>
        [JsonPropertyName("update")] public UpdateInfo? Update { get; set; }
        /// <summary>Protect LockGuard from administrators (see Hardening). Missing = keep the current setting.</summary>
        [JsonPropertyName("protect")] public bool? Protect { get; set; }
    }

    public sealed class UpdateInfo
    {
        [JsonPropertyName("build")] public int Build { get; set; }
        [JsonPropertyName("url")] public string Url { get; set; } = "";
        [JsonPropertyName("sha256")] public string Sha256 { get; set; } = "";
    }

    public sealed class DashboardCommand
    {
        [JsonPropertyName("type")] public string Type { get; set; } = "";
    }

    /// <summary>
    /// Registers if there's no token yet, then reports this computer's current state (and, when
    /// given, the sites looked up and log lines since last time) and returns what the dashboard
    /// wants applied -- or null if the dashboard can't be reached right now.
    /// </summary>
    public async Task<SyncResult?> SyncAsync(bool enabled, List<string> allowedPrograms, object info, List<object>? trafficLog = null, List<object>? events = null)
    {
        var state = LoadState();
        if (string.IsNullOrEmpty(state.Token))
        {
            var reg = await _http.PostAsJsonAsync($"{_baseUrl}/windows/register", new { info });
            if (!reg.IsSuccessStatusCode) return null;
            var regBody = await reg.Content.ReadFromJsonAsync<RegisterResponse>();
            if (string.IsNullOrEmpty(regBody?.Token)) return null;
            state.Token = regBody.Token;
            SaveState(state);
        }

        var req = new HttpRequestMessage(HttpMethod.Post, $"{_baseUrl}/windows/sync")
        {
            Content = JsonContent.Create(new { info, enabled, allowedPrograms, trafficLog = trafficLog ?? new List<object>(), events = events ?? new List<object>() }),
        };
        req.Headers.Authorization = new System.Net.Http.Headers.AuthenticationHeaderValue("Bearer", state.Token);
        var resp = await _http.SendAsync(req);
        if (resp.StatusCode == System.Net.HttpStatusCode.Unauthorized)
        {
            // The dashboard no longer knows this token (removed from the device list there) --
            // forget it locally too, so the next sync registers fresh instead of looping on 401s.
            SaveState(new DashboardState());
            return null;
        }
        if (!resp.IsSuccessStatusCode) return null;
        return await resp.Content.ReadFromJsonAsync<SyncResult>();
    }
}
