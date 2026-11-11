using System.Collections.Concurrent;
using System.Net;
using System.Net.Http.Headers;
using System.Net.Sockets;
using LockGuard.Common;

namespace LockGuard.Service;

/// <summary>
/// The filter itself: a website-name lookup server on this computer only (127.0.0.1 and ::1, port
/// 53). NetworkDns points every network connection at it and the firewall closes every other
/// lookup route, so every program's lookups pass through here. Each name goes through HostRules;
/// allowed names are looked up over an encrypted connection (DNS over HTTPS to Cloudflare, then
/// Google) and the answer is passed back. Blocked names get "0.0.0.0", so the site simply fails.
/// </summary>
public sealed class DnsProxy
{
    private static readonly string[] Upstreams = { "https://1.1.1.1/dns-query", "https://8.8.8.8/dns-query", "https://9.9.9.9/dns-query" };
    private readonly HttpClient _http = new(new SocketsHttpHandler { PooledConnectionLifetime = TimeSpan.FromMinutes(5) }) { Timeout = TimeSpan.FromSeconds(5) };
    private readonly ConcurrentDictionary<string, (byte[] reply, DateTime until)> _cache = new();
    private readonly Func<FilterPolicy> _policy;
    private readonly CategoryLists _lists;
    private readonly SiteLog _log;
    public bool Running { get; private set; }
    public string? LastError { get; private set; }

    public DnsProxy(Func<FilterPolicy> policy, CategoryLists lists, SiteLog log)
    {
        _policy = policy;
        _lists = lists;
        _log = log;
    }

    public void Start(CancellationToken ct)
    {
        if (Running) return;
        try
        {
            var v4 = new UdpClient(new IPEndPoint(IPAddress.Loopback, 53));
            _ = UdpLoop(v4, ct);
            try { _ = UdpLoop(new UdpClient(new IPEndPoint(IPAddress.IPv6Loopback, 53)), ct); } catch { /* no IPv6 on this computer */ }
            var t4 = new TcpListener(IPAddress.Loopback, 53);
            t4.Start();
            _ = TcpLoop(t4, ct);
            try { var t6 = new TcpListener(IPAddress.IPv6Loopback, 53); t6.Start(); _ = TcpLoop(t6, ct); } catch { }
            Running = true;
            LastError = null;
        }
        catch (Exception e)
        {
            // Something else already answers lookups on this computer (another filter, a VPN...).
            LastError = "Could not start the filter on port 53: " + e.Message;
        }
    }

    private async Task UdpLoop(UdpClient udp, CancellationToken ct)
    {
        while (!ct.IsCancellationRequested)
        {
            UdpReceiveResult r;
            try { r = await udp.ReceiveAsync(ct); }
            catch (OperationCanceledException) { return; }
            catch (SocketException) { continue; } // a client went away; keep serving
            _ = Task.Run(async () =>
            {
                var reply = await HandleAsync(r.Buffer);
                if (reply != null) { try { await udp.SendAsync(reply, reply.Length, r.RemoteEndPoint); } catch { } }
            });
        }
    }

    private async Task TcpLoop(TcpListener l, CancellationToken ct)
    {
        while (!ct.IsCancellationRequested)
        {
            TcpClient c;
            try { c = await l.AcceptTcpClientAsync(ct); }
            catch (OperationCanceledException) { return; }
            catch { continue; }
            _ = Task.Run(async () =>
            {
                using (c)
                {
                    var s = c.GetStream();
                    var lenBuf = new byte[2];
                    try
                    {
                        while (await s.ReadAsync(lenBuf.AsMemory(0, 2)) == 2)
                        {
                            int len = (lenBuf[0] << 8) | lenBuf[1];
                            var q = new byte[len];
                            int got = 0;
                            while (got < len) { int n = await s.ReadAsync(q.AsMemory(got, len - got)); if (n <= 0) return; got += n; }
                            var reply = await HandleAsync(q);
                            if (reply == null) return;
                            await s.WriteAsync(new[] { (byte)(reply.Length >> 8), (byte)reply.Length });
                            await s.WriteAsync(reply);
                        }
                    }
                    catch { }
                }
            });
        }
    }

    public async Task<byte[]?> HandleAsync(byte[] query)
    {
        var host = DnsMessage.QuestionName(query);
        int type = DnsMessage.QuestionType(query);
        if (host == null) return await ForwardAsync(query, null);
        var p = _policy();
        var level = p.Level;
        Func<string, bool> category = h => !_lists.Ready(level) ? !HostRules.Under(h, p.Essential) : _lists.Blocks(h); // fail closed until the lists are in
        var (verdict, target) = HostRules.Decide(host, p, category, DateTimeOffset.UtcNow.ToUnixTimeMilliseconds());
        if (p.On && (p.RecordSites || verdict == Verdict.Block)) _log.Add(host, verdict == Verdict.Block);
        if (verdict == Verdict.Block) return DnsMessage.Blocked(query);
        if (verdict == Verdict.Safe && target != null)
        {
            if (type == DnsMessage.TypeA)
            {
                var ips = await ResolveAAsync(target);
                if (ips.Count == 0) ips = HostRules.SafeFallback(target);
                return ips.Count > 0 ? DnsMessage.Answer(query, ips, 300) : DnsMessage.ServFail(query);
            }
            if (type == DnsMessage.TypeAAAA || type == DnsMessage.TypeHTTPS) return DnsMessage.Empty(query); // keep it on the safe IPv4 address
        }
        return await ForwardAsync(query, host + "|" + type);
    }

    private async Task<List<byte[]>> ResolveAAsync(string host)
    {
        var reply = await ForwardAsync(DnsMessage.BuildQuery(Random.Shared.Next(1, 65535), host), host + "|1");
        return reply == null ? new List<byte[]>() : DnsMessage.ParseA(reply);
    }

    private async Task<byte[]?> ForwardAsync(byte[] query, string? cacheKey)
    {
        if (cacheKey != null && _cache.TryGetValue(cacheKey, out var hit) && hit.until > DateTime.UtcNow) return DnsMessage.WithId(hit.reply, query);
        foreach (var url in Upstreams)
        {
            try
            {
                using var content = new ByteArrayContent(query);
                content.Headers.ContentType = new MediaTypeHeaderValue("application/dns-message");
                using var req = new HttpRequestMessage(HttpMethod.Post, url) { Content = content };
                req.Headers.Accept.Add(new MediaTypeWithQualityHeaderValue("application/dns-message"));
                using var resp = await _http.SendAsync(req);
                if (!resp.IsSuccessStatusCode) continue;
                var reply = await resp.Content.ReadAsByteArrayAsync();
                if (reply.Length < 12) continue;
                if (cacheKey != null)
                {
                    if (_cache.Count > 5000) _cache.Clear();
                    _cache[cacheKey] = (reply, DateTime.UtcNow.AddSeconds(60));
                }
                return DnsMessage.WithId(reply, query);
            }
            catch { /* try the next one */ }
        }
        return DnsMessage.ServFail(query);
    }

    public void ClearCache() => _cache.Clear();
}

/// <summary>Sites looked up since the last report, for the dashboard's "Sites visited".</summary>
public sealed class SiteLog
{
    private readonly ConcurrentDictionary<string, (int count, bool blocked, long at)> _hosts = new();

    public void Add(string host, bool blocked)
    {
        if (_hosts.Count > 2000) return;
        long now = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds();
        _hosts.AddOrUpdate(host, (1, blocked, now), (_, v) => (v.count + 1, v.blocked || blocked, v.at));
    }

    /// <summary>Takes everything collected so far, in the dashboard's traffic-log shape.</summary>
    public List<object> Drain(int max = 300)
    {
        var o = new List<object>();
        foreach (var k in _hosts.Keys.Take(max).ToList())
            if (_hosts.TryRemove(k, out var v)) o.Add(new { kind = "dns", host = k, time = v.at, blocked = v.blocked, app = "" });
        return o;
    }

    public bool Any => !_hosts.IsEmpty;
}
