# ADR-010 — Session-bound application suspension

**Status:** FST-028 design within the authorized sequential delivery.

## Consent and protection lifecycle

Add an installation-level application-protection preference, initially disabled.
The UI explains temporary suspension and restoration and requires an explicit
acknowledgement before enabling it. PATCH /api/v1/blocking-protection accepts
applicationsEnabled and, when enabling, consent=SUSPEND_MATCHED_APPS. Save the
flag and consent timestamp in additive migration V6 on the singleton policy.
Use the policy lock for preference changes and reset it during delete-all.
Include the preference in JSON/CSV exports. Existing enabled rules remain saved
configuration; only consented protection evaluates them during running work.

A foreground cycle evaluates the committed policy with the existing pure
service. LOG records detection, WARN also produces local learner feedback, and
BLOCK suspends the observed instance. Debounce repeated observations for the same
session/interval, instance and decision; a new instance is always considered.
Keep successful restrictions independently of foreground focus so changing
windows does not accidentally resume a blocked application.

A separate reconciliation worker checks session, expiration, preference and
current rule decisions every 500 ms. Release owned restrictions during pauses,
breaks, stop, interval/session changes, disabled/deleted rules, action changes,
consent revocation, shutdown or unavailable state. Read failures fail open by
releasing restrictions; do not maintain blocking based on stale cached policy.
Before and after native suspension, recheck current eligibility and decision.
No OS operation is performed while holding the database transaction lock.

## Native guardian and restoration

Use one independent guardian per backend, communicating over inherited stdin
and stdout with fixed numeric instance commands. No listening network socket or
user-supplied shell expression is introduced. The guardian restores its own
restrictions on EOF, explicit release/quit, shutdown and missing heartbeats.
A short lease (15 seconds maximum) bounds restrictions if the backend freezes.
A guardian crash/forced kill cannot promise immediate restoration on every OS;
record recovery information before suspension and use it for safe startup
recovery. Do not claim stronger guarantees than tested.

Linux/macOS use SIGSTOP/SIGCONT on the selected PID, checking process start
identity immediately before signaling and before release. Refuse processes
already stopped externally. Unix signal APIs have an unavoidable narrow race
between identity checking and signaling; never signal a reused PID during
recovery. No process group or child-tree termination is used. Multiple concurrent
external stop controllers are outside the ownership model; do not intentionally
resume restrictions known to predate FocusDesktop.

Windows uses documented thread handles and SuspendThread/ResumeThread, retaining
only increments introduced by this guardian. Restore only those increments,
including partial-suspension rollback, and preserve pre-existing suspend counts.
Recheck thread ownership against the selected process and bound enumeration of
newly created threads. Refuse a process whose threads cannot be safely handled.
This uses a fixed PowerShell/C# helper; permission failures are explicit, not
implicit requests for elevation or successful block outcomes.

Exclude the backend, guardian, desktop shell and their ancestors, plus critical
OS session/compositor processes, so rules cannot suspend the recovery path.
Confirm actual suspension before recording success. An exited process is a
safe no-longer-active result; a different process reusing its PID is never resumed.
The managed Java runtime can launch the Unix guardian through the packaged
Spring Boot PropertiesLauncher without requiring a user-installed runtime.
Windows uses the system PowerShell runtime. Verify packaged guardian launching.

## Durable outcomes and feedback

Commit DistractionDetected before native enforcement. Append DistractionBlocked
only after native confirmation, otherwise append DistractionBlockFailed with a
safe nonempty reason. Keep immutable target/decision snapshots and correlation
IDs. Serialize a versioned explicit DTO, including target type, rather than
relying on Jackson introspection of the pure domain model.

The outbox keeps distraction events durable for the FST-037 projection; session
dispatch must continue independently and must not mark distraction events as
delivered before their consumer exists. FST-037 adds projection/delivery. Expose
a correlated last warning/failure and current owned restriction count through
blocking status so the learner receives local feedback on every page. Do not
interpret configured capability as active enforcement.

## Verification and known limits

Test session/rule changes, disabled consent, precedence and action semantics,
debouncing, new instances, native failures, exit/PID reuse and cleanup. Test
real suspension/restoration only with child processes created by the test,
including guardian EOF/lease expiry and startup recovery. Never suspend an
unrelated live user application in automated checks. Verify transaction rollback,
outbox snapshots, API security, preference export/reset and UI consent/feedback.
Run full backend/frontend verification and packaged-helper smoke checks. Record
Windows/macOS native verification separately from Linux checks; mark unsupported
or denied configurations unavailable.

## Primary references

- [SuspendThread counts](https://learn.microsoft.com/en-us/windows/win32/api/processthreadsapi/nf-processthreadsapi-suspendthread)
- [ResumeThread counts](https://learn.microsoft.com/en-us/windows/win32/api/processthreadsapi/nf-processthreadsapi-resumethread)
- [Linux signal semantics](https://www.kernel.org/pub/linux/docs/man-pages/book/man-pages-6.11.pdf)
- [Packaged PropertiesLauncher](https://docs.spring.io/spring-boot/specification/executable-jar/property-launcher.html)
