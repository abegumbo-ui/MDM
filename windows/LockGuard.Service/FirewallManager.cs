using System.Runtime.Versioning;

namespace LockGuard.Service;

/// <summary>
/// Talks to the Windows Firewall through its COM API (HNetCfg.FwPolicy2 -- the same thing the
/// Windows Firewall control panel itself drives), late-bound via ProgID so this project needs no
/// extra COM interop package. Strategy: set the outbound default action to Block on every
/// profile, then add one Allow rule per allowed program path. Everything not explicitly allowed
/// has no internet access, full stop -- the program itself still opens, Windows just can't make
/// a connection out on its behalf.
///
/// NOT VERIFIED ON REAL WINDOWS: this was written without a Windows machine to test on. Two
/// things in particular need real-world checking before this is trusted on anyone's computer:
///   1. DNS: Windows usually resolves domain names through the "DNS Client" service running
///      inside a shared svchost.exe process, not inside the allowed program itself. This class
///      allows that path by its service name, but whether that's sufficient (vs. needing to
///      allow svchost.exe more broadly, which would be a bigger hole) needs confirming.
///   2. Whether an outbound default-block at the profile level actually blocks everything for an
///      app that isn't explicitly allowed, including apps that make raw/low-level connections.
/// </summary>
[SupportedOSPlatform("windows")]
public static class FirewallManager
{
    private const string RuleNamePrefix = "LockGuard-Allow-";
    private const string DnsRuleName = "LockGuard-Allow-DnsClient";
    private const string DhcpRuleName = "LockGuard-Allow-Dhcp";

    // NET_FW_ACTION_ and NET_FW_RULE_DIR_ and NET_FW_PROFILE2_ enum values, from the
    // INetFwPolicy2/INetFwRule COM interfaces (netfw.h), used directly since this project has no
    // interop assembly reference for them.
    private const int NET_FW_ACTION_BLOCK = 0;
    private const int NET_FW_ACTION_ALLOW = 1;
    private const int NET_FW_RULE_DIR_OUT = 2;
    private const int ALL_PROFILES = 0x7FFFFFFF; // Domain | Private | Public

    public static void ApplyLockdown(IEnumerable<string> allowedProgramPaths)
    {
        dynamic policy = CreatePolicy();

        // Default-deny outbound on every profile. Inbound is left alone -- this is about what the
        // computer can reach, not what can reach it.
        policy.DefaultOutboundAction[ALL_PROFILES] = NET_FW_ACTION_BLOCK;

        RemoveAllLockGuardRules(policy);

        AddAllowRule(policy, DnsRuleName, applicationName: null, serviceName: "Dnscache", protocol: 17 /* UDP */, remotePort: "53");
        AddAllowRule(policy, DhcpRuleName, applicationName: null, serviceName: "Dhcp", protocol: 17 /* UDP */, remotePort: "67,68");

        int i = 0;
        foreach (var path in allowedProgramPaths)
        {
            if (string.IsNullOrWhiteSpace(path) || !File.Exists(path)) continue;
            AddAllowRule(policy, RuleNamePrefix + i++, applicationName: path, serviceName: null, protocol: 256 /* ANY */, remotePort: null);
        }
    }

    /// <summary>Restores normal (allow-all) outbound networking and removes every rule this app added.</summary>
    public static void RemoveLockdown()
    {
        dynamic policy = CreatePolicy();
        policy.DefaultOutboundAction[ALL_PROFILES] = NET_FW_ACTION_ALLOW;
        RemoveAllLockGuardRules(policy);
    }

    private static dynamic CreatePolicy()
    {
        var type = Type.GetTypeFromProgID("HNetCfg.FwPolicy2")
            ?? throw new InvalidOperationException("Windows Firewall COM API (HNetCfg.FwPolicy2) is not available on this machine.");
        return Activator.CreateInstance(type)!;
    }

    private static void RemoveAllLockGuardRules(dynamic policy)
    {
        // Collecting names first: removing from a COM collection while enumerating it is unreliable.
        var toRemove = new List<string>();
        foreach (dynamic rule in policy.Rules)
        {
            string name = rule.Name ?? "";
            if (name.StartsWith(RuleNamePrefix, StringComparison.Ordinal)) toRemove.Add(name);
        }
        foreach (var name in toRemove) policy.Rules.Remove(name);
    }

    private static void AddAllowRule(dynamic policy, string name, string? applicationName, string? serviceName, int protocol, string? remotePort)
    {
        var ruleType = Type.GetTypeFromProgID("HNetCfg.FWRule")!;
        dynamic rule = Activator.CreateInstance(ruleType)!;
        rule.Name = name;
        rule.Description = "Added by LockGuard. Do not edit by hand -- it is re-applied automatically.";
        rule.Direction = NET_FW_RULE_DIR_OUT;
        rule.Action = NET_FW_ACTION_ALLOW;
        rule.Enabled = true;
        rule.Profiles = ALL_PROFILES;
        if (applicationName != null) rule.ApplicationName = applicationName;
        if (serviceName != null) rule.ServiceName = serviceName;
        rule.Protocol = protocol; // 256 = NET_FW_IP_PROTOCOL_ANY; set explicitly rather than relying on a default
        if (remotePort != null) rule.RemotePorts = remotePort;
        policy.Rules.Add(rule);
    }
}
