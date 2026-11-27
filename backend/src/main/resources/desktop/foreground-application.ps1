Add-Type -TypeDefinition @'
using System;
using System.Text;
using System.Runtime.InteropServices;
public static class FocusApplicationIdentity {
    public delegate bool Child(IntPtr window, IntPtr data);
    [DllImport("user32.dll")] public static extern IntPtr GetForegroundWindow();
    [DllImport("user32.dll")] public static extern uint GetWindowThreadProcessId(IntPtr window, out uint pid);
    [DllImport("user32.dll")] public static extern bool EnumChildWindows(IntPtr window, Child callback, IntPtr data);
    [DllImport("kernel32.dll")] static extern IntPtr OpenProcess(uint access, bool inherit, uint pid);
    [DllImport("kernel32.dll")] static extern bool CloseHandle(IntPtr handle);
    [DllImport("kernel32.dll", CharSet=CharSet.Unicode, ExactSpelling=true)] static extern int GetApplicationUserModelId(IntPtr process, ref uint length, StringBuilder id);
    public static string Identity(uint pid) {
        IntPtr process = OpenProcess(0x1000, false, pid);
        if (process == IntPtr.Zero) return null;
        try {
            uint length = 0;
            if (GetApplicationUserModelId(process, ref length, null) != 122 || length == 0 || length > 512) return null;
            var id = new StringBuilder((int)length);
            return GetApplicationUserModelId(process, ref length, id) == 0 ? id.ToString() : null;
        } finally { CloseHandle(process); }
    }
    public static uint Foreground(out string identity) {
        var window = GetForegroundWindow(); uint pid = 0;
        if (window != IntPtr.Zero) GetWindowThreadProcessId(window, out pid);
        identity = Identity(pid);
        if (pid == 0) return 0;
        string name;
        try { name = System.Diagnostics.Process.GetProcessById((int)pid).ProcessName; } catch { return 0; }
        if (!name.Equals("ApplicationFrameHost", StringComparison.OrdinalIgnoreCase)) return pid;
        uint app = 0; string found = null; bool ambiguous = false;
        EnumChildWindows(window, (child, data) => {
            uint candidate; GetWindowThreadProcessId(child, out candidate);
            if (candidate != 0 && candidate != pid) {
                string id = Identity(candidate);
                if (id != null) { if (app != 0 && app != candidate) ambiguous = true; app = candidate; found = id; }
            }
            return true;
        }, IntPtr.Zero);
        if (app == 0 || ambiguous) throw new InvalidOperationException("Hosted application identity unavailable");
        identity = found; return app;
    }
}
'@
if ($null -ne $FocusProcessId) {
    $found = [FocusApplicationIdentity]::Identity([uint32]$FocusProcessId)
    if ($found) { [Console]::Write('windows:' + $found) }
    return
}
$identity = $null
$foregroundPid = [FocusApplicationIdentity]::Foreground([ref]$identity)
ConvertTo-Json -Compress -InputObject @{pid=$foregroundPid;applicationId=$(if($identity){'windows:'+$identity}else{$null})}
