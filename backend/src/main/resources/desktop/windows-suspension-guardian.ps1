param([string]$JournalPath, [int]$ParentProcessId, [long]$ParentStartedMs, [string]$ProtectedPids)
$ErrorActionPreference = 'Stop'
Add-Type -ReferencedAssemblies @('System.dll', 'System.Core.dll', 'System.Web.Extensions.dll') -TypeDefinition @'
using System;
using System.IO;
using System.Text;
using System.Diagnostics;
using System.Collections.Generic;
using System.Collections.Concurrent;
using System.Runtime.InteropServices;
using System.Threading;
using System.Web.Script.Serialization;

public static class FocusSuspensionGuardian {
    [DllImport("kernel32.dll", SetLastError=true)] static extern IntPtr OpenThread(uint access, bool inherit, uint id);
    [DllImport("kernel32.dll", SetLastError=true)] static extern uint SuspendThread(IntPtr thread);
    [DllImport("kernel32.dll", SetLastError=true)] static extern uint ResumeThread(IntPtr thread);
    [DllImport("kernel32.dll", SetLastError=true)] static extern uint GetProcessIdOfThread(IntPtr thread);
    [DllImport("kernel32.dll", SetLastError=true)] static extern bool GetThreadTimes(IntPtr thread, out long created, out long exited, out long kernel, out long user);
    [DllImport("kernel32.dll")] static extern uint WaitForSingleObject(IntPtr handle, uint timeout);
    [DllImport("kernel32.dll")] static extern bool CloseHandle(IntPtr handle);
    const uint InvalidCount = 0xffffffff;
    static readonly JavaScriptSerializer Json = new JavaScriptSerializer();
    static readonly Dictionary<int, OwnedProcess> Owned = new Dictionary<int, OwnedProcess>();
    static readonly HashSet<int> Protected = new HashSet<int>();
    static readonly HashSet<string> Critical = new HashSet<string>(StringComparer.OrdinalIgnoreCase) {
        "focusdesktop", "explorer", "dwm", "winlogon", "csrss", "services", "lsass", "smss", "System", "Registry"
    };
    static string Journal;
    public class ThreadRecord { public int Id; public long Created; public uint PreviousCount; public bool Confirmed; }
    public class ProcessRecord { public int Pid; public long Started; public List<ThreadRecord> Threads = new List<ThreadRecord>(); }
    class ThreadLease { public IntPtr Handle; public ThreadRecord Record; public bool Incremented; }
    class OwnedProcess { public ProcessRecord Record; public List<ThreadLease> Threads = new List<ThreadLease>(); }
    class Refused : Exception { public string Code; public Refused(string code) { Code = code; } }
    static long Started(Process process) { return (process.StartTime.ToUniversalTime().Ticks - 621355968000000000L) / 10000; }
    static bool Same(int pid, long started) {
        try { using (var process = Process.GetProcessById(pid)) { return !process.HasExited && Started(process) == started; } }
        catch { return false; }
    }
    static void Reply(string value) { Console.WriteLine(value); Console.Out.Flush(); }

    public static void Run(string journal, int parentPid, long parentStarted, string protectedPids) {
        Journal = journal;
        Protected.Add(Process.GetCurrentProcess().Id); Protected.Add(parentPid);
        foreach (string text in (protectedPids ?? "").Split(',')) { int value; if (Int32.TryParse(text, out value)) Protected.Add(value); }
        Directory.CreateDirectory(Path.GetDirectoryName(journal));
        using (var fileLock = new FileStream(journal + ".lock", FileMode.OpenOrCreate, FileAccess.ReadWrite, FileShare.None)) {
            try {
                Recover();
                var lines = new BlockingCollection<string>();
                var input = new Thread(delegate() {
                    try { string line; while ((line = Console.ReadLine()) != null) lines.Add(line); }
                    catch { }
                    finally { lines.Add("__EOF__"); }
                });
                input.IsBackground = true; input.Start(); Reply("READY");
                while (Same(parentPid, parentStarted)) {
                    string line;
                    if (!lines.TryTake(out line, 15000) || line == "__EOF__") break;
                    try {
                        var command = Json.Deserialize<Dictionary<string, object>>(line);
                        string action = Convert.ToString(command["action"]);
                        int pid = Convert.ToInt32(command["pid"]); long started = Convert.ToInt64(command["startedAtEpochMs"]);
                        if (action == "QUIT") { RestoreAll(); Reply("OK"); break; }
                        if (action == "PING") Reply("OK");
                        else if (action == "SUSPEND") { Suspend(pid, started); Reply("OK"); }
                        else if (action == "RESUME") { Restore(pid, started); Reply("OK"); }
                        else if (action == "STATE") Reply(IsSuspended(pid, started) ? "SUSPENDED" : "RELEASED");
                        else if (action == "RELEASE_ALL") { RestoreAll(); Reply("OK"); }
                        else Reply("ERROR");
                    } catch (Refused refused) { Reply(refused.Code); }
                    catch { Reply("ERROR"); }
                }
            } finally { try { RestoreAll(); } catch { } }
        }
    }

    static void Suspend(int pid, long started) {
        if (!Same(pid, started)) throw new Refused("EXITED");
        if (Protected.Contains(pid) || pid <= 4) throw new Refused("PROTECTED");
        using (var process = Process.GetProcessById(pid)) {
            if (Critical.Contains(process.ProcessName)) throw new Refused("PROTECTED");
            if (Owned.ContainsKey(pid)) {
                if (IsSuspended(pid, started)) return;
                Restore(pid, started);
            }
            var owned = new OwnedProcess { Record = new ProcessRecord { Pid = pid, Started = started } };
            Owned.Add(pid, owned);
            try {
                var captured = new HashSet<int>();
                int stable = 0;
                for (int round = 0; round < 8 && stable < 2; round++) {
                    process.Refresh(); int added = 0;
                    foreach (ProcessThread thread in process.Threads) {
                        if (captured.Contains(thread.Id)) continue;
                        IntPtr handle = OpenThread(0x00100042, false, (uint)thread.Id);
                        if (handle == IntPtr.Zero) throw new InvalidOperationException();
                        long created, exited, kernel, user;
                        if (GetProcessIdOfThread(handle) != (uint)pid || !GetThreadTimes(handle, out created, out exited, out kernel, out user)) {
                            CloseHandle(handle); throw new InvalidOperationException();
                        }
                        var record = new ThreadRecord { Id = thread.Id, Created = created };
                        var lease = new ThreadLease { Handle = handle, Record = record };
                        owned.Threads.Add(lease); owned.Record.Threads.Add(record); Persist();
                        if (!Same(pid, started)) throw new InvalidOperationException();
                        uint previous = SuspendThread(handle);
                        if (previous == InvalidCount) throw new InvalidOperationException();
                        lease.Incremented = true; record.PreviousCount = previous; record.Confirmed = true; Persist();
                        captured.Add(thread.Id); added++;
                    }
                    stable = added == 0 ? stable + 1 : 0;
                }
                if (stable < 2 || owned.Threads.Count == 0) throw new InvalidOperationException();
            } catch { try { Restore(pid, started); } catch { } throw; }
        }
    }

    static bool IsSuspended(int pid, long started) {
        OwnedProcess owned;
        if (!Owned.TryGetValue(pid, out owned) || owned.Record.Started != started) return false;
        if (!Same(pid, started)) { Restore(pid, started); return false; }
        var activeThreads = new HashSet<int>();
        foreach (var lease in owned.Threads) {
            if (WaitForSingleObject(lease.Handle, 0) == 0) continue;
            activeThreads.Add(lease.Record.Id);
            uint previous = SuspendThread(lease.Handle);
            if (previous == InvalidCount) throw new InvalidOperationException();
            if (ResumeThread(lease.Handle) == InvalidCount) throw new InvalidOperationException();
            if (previous <= lease.Record.PreviousCount) return false;
        }
        using (var process = Process.GetProcessById(pid)) {
            process.Refresh(); foreach (ProcessThread thread in process.Threads) if (!activeThreads.Contains(thread.Id)) return false;
        }
        return true;
    }

    static void Restore(int pid, long started) {
        OwnedProcess owned;
        if (!Owned.TryGetValue(pid, out owned) || owned.Record.Started != started) return;
        bool failed = false;
        foreach (var lease in owned.Threads) {
            if (!lease.Incremented || WaitForSingleObject(lease.Handle, 0) == 0 || !Same(pid, started)) {
                lease.Incremented = false; continue;
            }
            // Probe the current count and withdraw the probe before removing our increment.
            uint previous = SuspendThread(lease.Handle);
            if (previous == InvalidCount) { failed = true; continue; }
            if (ResumeThread(lease.Handle) == InvalidCount) { failed = true; continue; }
            if (previous > lease.Record.PreviousCount && ResumeThread(lease.Handle) == InvalidCount) { failed = true; continue; }
            lease.Incremented = false; lease.Record.Confirmed = false;
        }
        if (failed) { Persist(); throw new InvalidOperationException(); }
        foreach (var lease in owned.Threads) CloseHandle(lease.Handle);
        Owned.Remove(pid); Persist();
    }
    static void RestoreAll() {
        bool failed = false;
        foreach (var item in new List<OwnedProcess>(Owned.Values)) {
            try { Restore(item.Record.Pid, item.Record.Started); } catch { failed = true; }
        }
        if (failed) throw new InvalidOperationException();
    }

    static void Recover() {
        if (!File.Exists(Journal)) return;
        if (new FileInfo(Journal).Length > 1048576) throw new InvalidOperationException();
        var records = Json.Deserialize<List<ProcessRecord>>(File.ReadAllText(Journal));
        var ambiguous = new List<ProcessRecord>();
        foreach (var record in records) {
            if (!Same(record.Pid, record.Started) || Protected.Contains(record.Pid)) continue;
            var owned = new OwnedProcess { Record = record }; Owned[record.Pid] = owned;
            var uncertain = new ProcessRecord { Pid = record.Pid, Started = record.Started };
            foreach (var item in record.Threads) {
                IntPtr handle = OpenThread(0x00100042, false, (uint)item.Id);
                if (handle == IntPtr.Zero) {
                    if (Marshal.GetLastWin32Error() != 87) uncertain.Threads.Add(item);
                    continue;
                }
                long created, exited, kernel, user;
                if (GetProcessIdOfThread(handle) != (uint)record.Pid || !GetThreadTimes(handle, out created, out exited, out kernel, out user) || created != item.Created) {
                    CloseHandle(handle); continue;
                }
                if (!item.Confirmed) {
                    uint previous = SuspendThread(handle);
                    if (previous != InvalidCount) ResumeThread(handle);
                    if (previous != 0) uncertain.Threads.Add(item);
                    CloseHandle(handle); continue;
                }
                owned.Threads.Add(new ThreadLease { Handle = handle, Record = item, Incremented = true });
            }
            if (uncertain.Threads.Count > 0) ambiguous.Add(uncertain);
        }
        RestoreAll();
        // An interrupted operation with no confirmed increment cannot safely be guessed.
        if (ambiguous.Count > 0) { WriteRecords(ambiguous); Reply("RECOVERY_REQUIRED"); throw new InvalidOperationException(); }
        Persist();
    }

    static void Persist() {
        if (Owned.Count == 0) { if (File.Exists(Journal)) File.Delete(Journal); return; }
        var records = new List<ProcessRecord>(); foreach (var item in Owned.Values) records.Add(item.Record);
        WriteRecords(records);
    }
    static void WriteRecords(List<ProcessRecord> records) {
        string temporary = Journal + "." + Guid.NewGuid().ToString("N") + ".tmp";
        try {
            byte[] bytes = new UTF8Encoding(false).GetBytes(Json.Serialize(records));
            using (var file = new FileStream(temporary, FileMode.CreateNew, FileAccess.Write, FileShare.None)) {
                file.Write(bytes, 0, bytes.Length); file.Flush(true);
            }
            if (File.Exists(Journal)) File.Replace(temporary, Journal, null); else File.Move(temporary, Journal);
        } finally { if (File.Exists(temporary)) File.Delete(temporary); }
    }
}
'@
[FocusSuspensionGuardian]::Run($JournalPath, $ParentProcessId, $ParentStartedMs, $ProtectedPids)
