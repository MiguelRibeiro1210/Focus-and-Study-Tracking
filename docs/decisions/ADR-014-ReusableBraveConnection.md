# ADR-014: Reuse Brave's local connection

**Status:** Approved by the user, 2026-11-27. FST-050 follow-up.

## Decision

Keep a stable loopback port and PAC identifier for Brave in the installation's
data directory. Reuse them after disabling/re-enabling protection and after a
backend restart. Session transitions and rule edits already reuse the running
proxy. System routing keeps its existing guardian and ephemeral endpoint.

Persist only the port and PAC identifier, atomically, after a successful bind.
Reject malformed, oversized or symbolic-link configuration. If the saved port
is occupied, report unavailable rather than silently changing the connection.
Do not persist connection proof: each proxy instance has a new verification
challenge, separate from the stable PAC identifier.

When restoring a saved Brave connection and this user's Brave is already open,
open the new verification page once in that existing instance using fixed,
validated arguments. Never close browsers, change their profiles, or launch a
closed browser automatically. A successful launch is not connection proof.
The existing explicit setup action remains available for initial setup or a
browser launched without the proxy flag. It still waits for voluntary exit.
Once verified, requesting setup does nothing and preserves that proof.

Disabling protection still revokes forwarding consent, closes the listener and
cancels pending setup. Retaining endpoint metadata does not authorize traffic.
No always-running forwarding helper or browser extension is introduced.

## Interface and limits

Keep compact Settings controls and the start-session readiness warning. Explain
in the connection dialog that session changes need no restart, a saved connection
is checked automatically on reuse, and first-time setup still needs one voluntary
Brave restart. Offer a Check connection link before repeating full setup.

Brave may temporarily bypass an unavailable proxy, or lose launch flags when
the browser itself is restarted normally. Neither a stable address nor a running
process guarantees protection. Never show Connected until the fresh challenge
has reached the proxy. If verification fails, explain the unverified connection
and retain the user's choice to start without blocking. No claim of coverage for
other browser profiles, unsupported packaging, or other operating systems is added.

## Verification

Backend tests cover stable endpoint reuse across runtime instances and toggles,
fresh proof, occupied/corrupt configuration, no forwarding after disable, one
automatic check per restoration, no cold-browser launch, and verified setup as
a no-op. A native isolated Brave test proves reconnecting to the same endpoint
without restarting the browser, plus fresh proof and denial after reconnection.
Frontend tests cover the connection check and updated setup instructions.
Run the backend suite, frontend tests/build/lint/privacy audit, and inspect the
diff before updating the board. Existing FST-050 native acceptance gaps stay open.
