# ADR-011 — Extension-free website protection

**Status:** Design for FST-050 within the authorized blocking delivery.
**Decision date:** 2026-11-26.

## Approved privacy exception

The user explicitly approved forwarding browser-requested traffic through a
local proxy, without HTTPS decryption, telemetry or a cloud service. This is a
narrow exception to FST-026 and ADR-001: only the consented website proxy may
open remote connections, and only to serve a browser request. Timer, local API,
storage, exports and application monitoring remain local. No certificate is
installed. TLS bytes pass unchanged through CONNECT tunnels. Do not retain URL
paths, queries, credentials, cookies, page contents or packet payloads.

Website protection is initially disabled. Enabling requires a separate explicit
acknowledgement explaining forwarding and temporary network configuration.
Record consent locally and include it in export/reset behavior. Consent to
application suspension does not imply website-proxy consent.

## Identification and enforcement

Bind an HTTP proxy exclusively to loopback. HTTP absolute-form requests and
HTTPS CONNECT authorities provide the requested host; window titles and DNS
cache inspection are not used. Reuse WebsiteTarget normalization, dot-boundary
subdomain matching and the most specific enabled rule. Never block the whole
browser to enforce a website rule. Reject malformed/ambiguous authorities,
unsupported protocols and unsafe proxy requests. Bound concurrent connections,
headers, timeouts and memory. Do not become a LAN-accessible open proxy.

Evaluate committed session/interval, preference and policy at each request.
Only running, unexpired WORK intervals generate distraction detections or
enforcement. LOG records a detection; WARN also publishes local feedback; BLOCK
denies the matching request before connecting to its destination. Commit the
detection before enforcement and record success only after denial is applied.
Use the existing immutable event snapshots and correlated outcomes. Failures
must have a safe reason. Bounded debounce avoids logging every subresource.

Allowed traffic is forwarded normally. HTTPS remains encrypted; blocking HTTPS
therefore produces a browser connection/proxy error, not a fabricated encrypted
block page. HTTP forwarding must validate each request authority and framing;
checking the first request then blindly relaying a persistent HTTP connection
would allow later authorities to bypass rules.

Reconcile existing tunnels when focus starts or policy/consent changes. Close
only tunnels whose current decision becomes BLOCK so a browser cannot retain
an allowed connection into a protected interval. Pause, break, stop, expiry,
rule disabling and consent revocation permit subsequent requests. Session/read
failures fail open and must not retain a stale blocking decision.

## Browser routing and restoration

Use a PAC file served on loopback to route HTTP(S) through the proxy while
bypassing local endpoints. Keep policy evaluation in the proxy rather than PAC:
browsers cache PAC scripts, so changing the script alone cannot implement timely
focus/pause transitions. No hosts-file approximation: hosts entries cannot
represent arbitrary subdomains.

Native configuration adapters must snapshot original settings before changes,
journal ownership, and restore only settings that still equal FocusDesktop's
applied values. Preserve concurrent user changes and existing enterprise proxy
configuration; refuse unsupported combinations with a clear explanation.
An independent lease/recovery mechanism restores owned settings after backend
disconnect or restart. Restore before shutting down the proxy. Where supported,
a verified PAC DIRECT fallback also prevents a dead proxy from stranding normal
browsing; intentional denials must never trigger that fallback.

Target Windows user proxy settings, macOS network-service proxy settings and
supported Linux desktop proxy settings. Required OS authorization must be
explicit. Do not execute a user-writable script with elevated privileges.
Browsers can override system settings; Firefox supports separate connection
settings, and Chromium launch flags do not reconfigure an already running
browser. Do not silently restart browsers or overwrite live profile files.

Expose setup, permission failure, unsupported settings, recovery and routing
verification in the UI. A local request challenge must establish that a browser
actually routes through this proxy before claiming it is protected. A saved
preference, reachable PAC file or successful settings command alone is not
proof. Browser/OS combinations remain unverified until their native checks pass.
VPNs, managed browser policies, custom proxies and traffic that bypasses the
configured proxy must be reported as limitations rather than universal blocking.

## Delivery and verification

Implement backend policy gating, bounded proxy transport, consent persistence,
owned settings/recovery and meaningful tests before the frontend setup/status
flow. Tests use owned local origin servers and controlled sockets, not remote
websites or live user network settings. Cover normalized domains/subdomains,
allowed hosts, LOG/WARN/BLOCK, CONNECT byte transparency, malformed input,
session transitions, existing tunnel closure, preference reset, API security,
failure feedback, ownership and crash restoration.

Keep the existing no-external-network smoke with website consent off. Add a
separate consented forwarding test that permits only the test origin and proves
unrequested traffic is absent. Native Windows/macOS/Linux and browser checks
must exercise setup, HTTPS denial, allowed browsing and automatic restoration;
the story remains open wherever acceptance evidence is missing.

## Primary references

- [Chromium proxy semantics, CONNECT, fallback and PAC caching](https://github.com/chromium/chromium/blob/main/net/docs/proxy.md).
- [Chromium proxy configuration](https://www.chromium.org/developers/design-documents/network-settings/).
- [Firefox connection settings](https://support.mozilla.org/en-US/kb/connection-settings-firefox).
- [Firefox managed proxy policy](https://firefox-admin-docs.mozilla.org/reference/policies/proxy/).

Modern Chromium limits fallback for intentional CONNECT denials, but this must
also be verified for each supported browser before enabling a DIRECT fallback.
