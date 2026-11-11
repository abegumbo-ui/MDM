using System.Text;

namespace LockGuard.Common;

/// <summary>
/// The few pieces of the DNS wire format the filter needs: read the question, build a "blocked"
/// answer, a SafeSearch answer, an empty answer, and a query of our own. Same logic as the phones'
/// SafeSearchDns.java / DnsFilterVpnService.
/// </summary>
public static class DnsMessage
{
    public const int TypeA = 1, TypeAAAA = 28, TypeHTTPS = 65;

    /// <summary>Index just past the (single) question, or -1.</summary>
    public static int QuestionEnd(byte[]? m)
    {
        if (m == null || m.Length < 17 || ((m[4] << 8) | m[5]) != 1) return -1;
        int pos = 12, guard = 0;
        while (pos < m.Length && guard++ < 128)
        {
            int len = m[pos];
            pos++;
            if (len == 0) break;
            if ((len & 0xc0) != 0) return -1;
            pos += len;
        }
        pos += 4;
        return pos > m.Length ? -1 : pos;
    }

    public static string? QuestionName(byte[]? m)
    {
        if (QuestionEnd(m) < 0) return null;
        var sb = new StringBuilder();
        int pos = 12;
        while (true)
        {
            int len = m![pos++];
            if (len == 0) break;
            if (sb.Length > 0) sb.Append('.');
            sb.Append(Encoding.ASCII.GetString(m, pos, len));
            pos += len;
        }
        return sb.Length == 0 ? null : sb.ToString().ToLowerInvariant();
    }

    public static int QuestionType(byte[]? m)
    {
        int end = QuestionEnd(m);
        return end < 0 ? -1 : (m![end - 4] << 8) | m[end - 3];
    }

    private static byte[] Header(byte[] query, int rcode, int answers)
    {
        return new byte[]
        {
            query[0], query[1],
            (byte)(0x81 | (query[2] & 0x00)), // response, recursion desired
            (byte)(0x80 | (rcode & 0x0f)), // recursion available + result code
            0, 1, (byte)(answers >> 8), (byte)answers, 0, 0, 0, 0,
        };
    }

    /// <summary>"Found, at these IPv4 addresses" -- the reply keeps the question's own name.</summary>
    public static byte[]? Answer(byte[] query, IReadOnlyList<byte[]> ips, int ttl)
    {
        int qEnd = QuestionEnd(query);
        if (qEnd < 0) return null;
        var o = new List<byte>(Header(query, 0, ips.Count));
        o.AddRange(query.AsSpan(12, qEnd - 12).ToArray());
        foreach (var ip in ips)
        {
            o.AddRange(new byte[] { 0xc0, 0x0c, 0, TypeA, 0, 1, (byte)(ttl >> 24), (byte)(ttl >> 16), (byte)(ttl >> 8), (byte)ttl, 0, 4 });
            o.AddRange(ip.Take(4));
        }
        return o.ToArray();
    }

    /// <summary>"No such address" for this type (no error, no answers).</summary>
    public static byte[]? Empty(byte[] query)
    {
        int qEnd = QuestionEnd(query);
        if (qEnd < 0) return null;
        var o = new List<byte>(Header(query, 0, 0));
        o.AddRange(query.AsSpan(12, qEnd - 12).ToArray());
        return o.ToArray();
    }

    /// <summary>"Server failure" -- the asking program tries again later.</summary>
    public static byte[]? ServFail(byte[] query)
    {
        int qEnd = QuestionEnd(query);
        if (qEnd < 0) return null;
        var o = new List<byte>(Header(query, 2, 0));
        o.AddRange(query.AsSpan(12, qEnd - 12).ToArray());
        return o.ToArray();
    }

    /// <summary>Blocked: IPv4 asks get 0.0.0.0 (fails at once, never cached long), anything else gets no answer.</summary>
    public static byte[]? Blocked(byte[] query)
    {
        return QuestionType(query) == TypeA ? Answer(query, new[] { new byte[] { 0, 0, 0, 0 } }, 10) : Empty(query);
    }

    public static byte[] BuildQuery(int id, string host, int type = TypeA)
    {
        var o = new List<byte> { (byte)(id >> 8), (byte)id, 0x01, 0x00, 0, 1, 0, 0, 0, 0, 0, 0 };
        foreach (var label in host.Split('.'))
        {
            var b = Encoding.ASCII.GetBytes(label);
            o.Add((byte)b.Length);
            o.AddRange(b);
        }
        o.AddRange(new byte[] { 0, (byte)(type >> 8), (byte)type, 0, 1 });
        return o.ToArray();
    }

    private static int SkipName(byte[] m, int pos)
    {
        int guard = 0;
        while (pos < m.Length && guard++ < 128)
        {
            int len = m[pos];
            if (len == 0) return pos + 1;
            if ((len & 0xc0) == 0xc0) return pos + 2;
            pos += 1 + len;
        }
        return -1;
    }

    /// <summary>The IPv4 addresses in a reply.</summary>
    public static List<byte[]> ParseA(byte[] reply)
    {
        var o = new List<byte[]>();
        int pos = QuestionEnd(reply);
        if (pos < 0 || (reply[3] & 0x0f) != 0) return o;
        int answers = (reply[6] << 8) | reply[7];
        for (int i = 0; i < answers && pos > 0 && pos < reply.Length; i++)
        {
            pos = SkipName(reply, pos);
            if (pos < 0 || pos + 10 > reply.Length) break;
            int type = (reply[pos] << 8) | reply[pos + 1];
            int len = (reply[pos + 8] << 8) | reply[pos + 9];
            pos += 10;
            if (pos + len > reply.Length) break;
            if (type == TypeA && len == 4) o.Add(new[] { reply[pos], reply[pos + 1], reply[pos + 2], reply[pos + 3] });
            pos += len;
        }
        return o;
    }

    /// <summary>A copy of a reply with another query's id (for answers served from the cache).</summary>
    public static byte[] WithId(byte[] reply, byte[] query)
    {
        var r = (byte[])reply.Clone();
        r[0] = query[0];
        r[1] = query[1];
        return r;
    }
}
