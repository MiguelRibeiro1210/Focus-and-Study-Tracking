# ADR-011 — Extension-free website protection

**Status:** Design for FST-050 within the authorized blocking delivery.
**Decision date:** 2026-11-26.

## Approved privacy exception

The user explicitly approved forwarding requests from browsers and other apps
that use the temporary system proxy, without HTTPS decryption, telemetry or a
cloud service. The system proxy cannot reliably identify a client executable
from its HTTP request. This is a narrow exception to FST-026 and ADR-001: only
the consented website proxy may open remote connections, and only in response
to a client request. It must never initiate autonomous outbound requests. Timer, local API,
storage, exports and application monitoring remain local. No certificate is
installed. TLS bytes pass unchanged through CONNECT tunnels. Do not retain URL
paths, queries, credentials, cookies, page contents or packet payloads.

Website protection is initially disabled. Enabling requires a separate explicit
acknowledgement explaining forwarding and temporary network configuration.
Record consent locally and include it in export/reset behavior. Consent to
application suspension does not imply website-proxy consent.

The API acknowledgement is `FORWARD_SYSTEM_PROXY_TRAFFIC`. Reject the previous
browser-only acknowledgement instead of treating it as consent to a broader
scope. A forward-only migration disables website protection and clears its old
consent timestamp so existing installations must explicitly accept the new
scope. Keep saved rules and application protection unchanged. Startup recovery
must still restore any settings journal left by the previous process before
closing its proxy. Export/reset continue to include/clear the current consent.
The UI must name browsers and other apps using temporary system proxy settings,
state that HTTPS is not decrypted, and disclaim autonomous requests or telemetry.
A browser verification challenge proves that client's routing only; it cannot
prove that all apps use the proxy or identify the client's executable.

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

## Implemented boundaries and outstanding native verification

The current adapters use Windows per-user PAC settings, macOS network-service
PAC settings and Linux GNOME/GSettings. Existing manual, automatic-discovery or
PAC configurations that would be replaced are refused. KDE and desktops without
the required GSettings integration remain unsupported. Native commands run with
the user's permissions; macOS configurations requiring elevated authorization
remain unavailable until a reviewed authorization adapter exists. There is no
silent elevation or privileged installation in this implementation.

A separate Java guardian journals settings, verifies application, checks ownership
on heartbeats and restores on EOF or a 15-second lease expiry. A user replacement
configuration disables website protection and is preserved. Forced termination
of both backend and guardian requires startup recovery; immediate restoration
after that event is not claimed. The PAC intentionally has no DIRECT fallback
until intentional-denial behavior has been verified across the target browsers.

The transport bounds connections to 64, headers to 32 KiB and idle reads to 60
seconds. HTTPS is an opaque tunnel. Plain HTTP accepts one request per connection,
with a declared body of at most 10 MiB; chunked uploads, protocol upgrades,
Expect handshakes and IP-literal destinations are rejected explicitly. Local
destinations must bypass the proxy. These limits require compatibility testing;
do not call the all-browser acceptance criteria complete from transport tests.

Controlled Linux Firefox testing verifies PAC retrieval, local routing proof,
allowed HTTP, blocked HTTP and HTTPS CONNECT denial without destination traffic.
Native Windows/macOS browser configuration and
restoration, Chrome/Edge/Safari routing, Linux desktop integration and real HTTPS
compatibility remain separate acceptance checks. The story stays open for gaps.

## Primary references

- [Chromium proxy semantics, CONNECT, fallback and PAC caching](https://github.com/chromium/chromium/blob/main/net/docs/proxy.md).
- [Chromium proxy configuration](https://www.chromium.org/developers/design-documents/network-settings/).
- [Firefox connection settings](https://support.mozilla.org/en-US/kb/connection-settings-firefox).
- [Firefox managed proxy policy](https://firefox-admin-docs.mozilla.org/reference/policies/proxy/).

Modern Chromium limits fallback for intentional CONNECT denials, but this must
also be verified for each supported browser before enabling a DIRECT fallback.

## Linux Brave / Hyprland routing (approved follow-up)

Offer explicit `BRAVE` routing alongside the existing `SYSTEM_PROXY` method.
Persist the selection with website preferences (default `SYSTEM_PROXY` for
existing installations). Brave routing never changes GNOME, Hyprland or browser
profile files. Initially support native Linux Brave at known installation paths;
unsupported packaging is reported rather than silently configured.

Enabling protection still requires forwarding consent and an explicitly enabled
native backend. Browser development uses `npm run dev` with Spring Boot argument
`--focusdesktop.blocking.monitor-enabled=true`; ordinary backend runs remain
passive. Tauri development already enables the runtime. The setup UI exposes
runtime availability separately from saved consent and verified browser routing.

Authenticated `POST /api/v1/website-protection/brave` accepts only
`{"confirmation":"OPEN_EXISTING_BRAVE_PROFILE"}` and returns current status with
HTTP 202. It schedules a 60-second wait for this user's Brave processes to exit,
then launches a known native executable using fixed arguments for the current
PAC and verification address. No shell, supplied executable, profile override,
forced termination or automatic launch on startup is allowed. This also works
when the development UI itself is in Brave: the learner closes all windows,
including the UI, and the backend performs the explicitly requested launch.
Timeout/cancellation/launch failure is visible on the next status read. Disabling
protection or changing routing cancels any pending launch.

Status adds `runtimeEnabled`, `routingMethod`, `braveAvailable` and
`braveLaunchPending`. PATCH optionally accepts `routingMethod`; old clients keep
the saved method. A routing change restores owned system settings before opening
a different proxy. Export/reset include/reset the selected method. Verify the
local challenge before reporting connected routing; a saved flag or process
launch is not proof that all browser profiles are protected.

The Brave-only PAC includes `DIRECT` fallback after isolated native
Brave tests prove intentional HTTP/HTTPS denials cannot fall through to a direct
origin, while shutdown/crash allows normal browsing. Other platform PAC behavior
is unchanged. Protection is not tamper-proof and does not cover clients bypassing
the configured proxy. Existing Windows/macOS/GNOME behavior and outstanding
native authorization acceptance gaps remain tracked separately.

Native Brave verification now covers controlled HTTP/HTTPS origins, YouTube's
built-in HSTS upgrade, subdomain denial, pause/resume and both normal shutdown
and abrupt fixture-process termination. This is network-request enforcement;
cached/offline page content may remain visible. Related media/CDN domains are
not silently added to a domain rule. Tests isolate both the profile and launcher
configuration and never close the learner's existing browser.
