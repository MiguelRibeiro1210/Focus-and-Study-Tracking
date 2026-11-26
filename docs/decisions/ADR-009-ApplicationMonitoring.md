# ADR-009 — Foreground application monitoring

**Status:** FST-027 design, authorized as part of sequential blocking delivery.

## Runtime and privacy boundary

Run application monitoring in the managed Java backend, where committed session
state and rule storage already live. The Tauri launcher explicitly enables the
monitor with `--focusdesktop.blocking.monitor-enabled=true`; ordinary backend
launches and automated backend tests leave it disabled unless explicitly enabled.
Use a dedicated scheduler thread so slow OS calls cannot delay timer completion.

Every two seconds, read the active session. Query the OS only for RUNNING WORK
with a RUNNING interval whose remaining time is positive. Recheck the same
session and interval after detection; discard observations if it changed,
paused, expired or stopped during the OS call. Do not collect outside work.
The monitor does not scan all processes, read command arguments, capture screens
or infer websites from titles. Returned compositor metadata is discarded after
extracting the foreground PID. Do not persist raw tool output or process identity
in diagnostic logs. Observation identity is executable path/process name with a
transient PID and process start instant. Missing identity is a capability failure;
never fabricate an executable from a window title or class.

## Platform adapters

| Platform | Foreground process | Requirements / limits |
| --- | --- | --- |
| Windows | Fixed non-interactive PowerShell script calls user32 GetForegroundWindow and GetWindowThreadProcessId | PowerShell and access to foreground process metadata; no elevation for ordinary same-user apps |
| macOS | Fixed JXA script via osascript reads AppKit NSWorkspace.frontmostApplication.processIdentifier | Logged-in graphical session; permission denial is explicit; no title scraping or System Events automation |
| Linux X11 | xprop reads _NET_ACTIVE_WINDOW, then _NET_WM_PID | xprop and an EWMH-compatible local X11 window manager |
| Linux Hyprland | hyprctl -j activewindow, PID only | Live Hyprland IPC access; native Wayland supported |
| Linux Sway | swaymsg -t get_tree -r, focused leaf PID | Live Sway IPC access; native Wayland supported |
| Other Wayland | Unavailable | No generic foreground PID API assumed; do not silently fall back to XWayland and miss native windows |

Java ProcessHandle resolves only the selected PID's executable and start time.
A process exiting during lookup is a no-observation result. A missing start time
or unusable path is explicit unavailable identity. Validate process metadata with
the existing AppIdentifier domain contract; store no command line or arguments.

Use fixed argument arrays without a shell. Bound OS commands with timeouts,
limit captured output and discard stderr, returning safe diagnostics. Parse only
numeric PIDs and window IDs; never interpolate user rule text into OS commands.
Do not probe or prompt for permissions outside an eligible session.

## Integration contract

Application ports return an optional transient observation or an explicit
failure. FST-028 will consume the observation synchronously, evaluate committed
rules, recheck eligibility and suspend/resume specific process instances.
FST-027 itself does not block, warn, or emit fictional successful outcomes.

Expose authenticated `GET /api/v1/blocking-status` returning monitorEnabled,
applicationDetection (DISABLED, IDLE, OBSERVING, UNAVAILABLE), applicationReason,
applicationEnforcement=false, websiteEnforcement=false and checkedAt. The read
endpoint never triggers an OS probe. UI refreshes this status while open and
clearly reports measured detection availability separately from enforcement.
Do not return raw application identifiers from this diagnostic endpoint.

## Verification

Test all session eligibility states, expiration, transition during detection,
missing processes, capability failures and failure recovery. Test platform
selection and command/PID parsing with injected command/process readers. Exercise
a controlled local process for ProcessHandle identity without collecting unrelated
processes. Verify status auth and UI unavailable/error states. Windows/macOS
native verification remains distinct from Linux automated checks and must be
recorded explicitly, including any unavailable desktop configurations.

## Sources and diagrams

- [Windows foreground window](https://learn.microsoft.com/en-us/windows/win32/api/winuser/nf-winuser-getforegroundwindow)
- [Window PID](https://learn.microsoft.com/en-us/windows/win32/api/winuser/nf-winuser-getwindowthreadprocessid)
- [macOS frontmost application](https://developer.apple.com/documentation/appkit/nsworkspace/frontmostapplication?language=objc)
- [Java process metadata](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ProcessHandle.Info.html)
- [EWMH root window](https://specifications.freedesktop.org/wm-spec/latest/ar01s03.html)
- [Hyprland IPC tool](https://wiki.hypr.land/configuring/core/advanced-configuration/using-hyprctl/)
- [Sway IPC](https://github.com/swaywm/sway/blob/master/sway/sway-ipc.7.scd)
- [Monitoring sequence](../SD/SD_ApplicationMonitoring.puml)
