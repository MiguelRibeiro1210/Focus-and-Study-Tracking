# ADR-015: Persistent browser routing across desktop platforms

**Status:** Approved scope, 2026-11-27: make website blocking work across Windows,
macOS and Linux without repeated browser closure. FST-050 remains In Progress
until platform acceptance evidence is complete.

## Routing and lifetime

The automatic connection replaces temporary browser launch arguments as the
normal setup. Windows uses per-user browser policies for Brave, Chrome, Chromium,
Edge and Firefox, together with the user system PAC. Linux installs owned proxy
policies for native desktop browsers, including on Hyprland; it does not pretend
GNOME settings control other desktops. macOS applies PAC settings to active
network services through an administrator-authorized helper, covering Safari
and browsers configured to use system settings. Existing custom proxies and
enterprise proxy policies are preserved; conflicting configuration is unavailable.

Keep the stable loopback endpoint introduced by ADR-014. Browser policies remain
installed while website protection is enabled, including across browser exits
and ordinary relaunches. Session/rule changes only change proxy decisions.
Do not launch or force-close a browser to apply automatic setup. Chromium policy
refresh can update open browsers. Firefox may need one initial policy-load
restart; it must not need restarting for each session or ordinary relaunch.
For supported Chromium executables, send `--refresh-platform-policy` to the
current user's existing instance, retaining only its absolute user-data directory
argument. Verify switch support before invoking it: older binaries must not be
launched with an unknown switch. This operation neither opens a new window nor
requests cloud policy. Older/unsupported instances can use Reload policies on
their policy page. Never pass browsing URLs or proxy flags from process arguments.

Browser packaging and overriding preferences still matter: native system packages
are supported on Linux; sandboxed Flatpak/Snap policy deployment is separate.
macOS browsers with custom proxy overrides require correction/verification and
are not silently reported protected. No verification request can reliably prove
the identity or coverage of every executable/profile. Label proof as a browser
connection check and require users to check each browser they use.

## Permission and restoration

The user authorized administrator prompts and persistent browser policies.
In-product consent must explain that Linux policies affect all users/profiles,
may show a managed-browser notice, and are restored when protection ends.
Windows settings are per-user. Do not overwrite existing managed proxy policies.

Use a separate lease guardian, with fixed setting keys and validated loopback
PAC values. Linux uses PolicyKit (`pkexec`, with an authentication agent); macOS
uses the OS administrator prompt. Elevated guardians connect to a temporary
loopback control socket with a fresh secret, then watch parent identity and
heartbeats. No API bearer token, browsing traffic or destination data enters
this channel. Elevated journals live in a fixed administrator-owned directory;
user-provided paths must never choose privileged destinations. Windows keeps
the user-owned guardian/journal and needs no elevation for user policies.
The new acknowledgement is `CONFIGURE_BROWSER_ROUTING_AND_FORWARD_TRAFFIC`;
old forwarding-only consent is rejected for automatic routing. V11 clears saved
consent/enabled state and selects automatic connection while preserving rules.
Control secrets are read from private temporary files owned by the live parent,
not exposed on command lines; delete them after the handshake. AppImage Java and
JAR resources are staged outside FUSE when necessary so the elevated helper can
read them, then removed after helper exit. Debian declares its `pkexec` dependency.

Restore owned values on disable, parent death, control disconnect and lease
expiry. Independent browser policies restore independently: a user's change to
one policy must not leave other owned policies installed. Firefox proxy updates
merge into its policy document, retaining unrelated keys. Recover a stale owned
journal before applying new settings. Never remove or replace another owner's
configuration. Do not repeat permission prompts on a timer after denial; retry
requires explicit setup. Preserve old routing API compatibility, but migrate
saved launch-only setup to automatic connection with protection off until fresh
consent. The old Brave-launch endpoint remains legacy, not the recommended UI.
Stop forwarding immediately on revocation, then complete restoration. Require
an explicit `RESTORED` acknowledgement from elevated guardians; if it is missing,
retain a pending recovery state and offer an explicit restoration retry without
enabling forwarding. A helper process exit alone is not restoration proof.

## Interface and verification

Use one Automatic connection option by default. Capability status distinguishes
missing runtime, missing authorization tools, saved consent, applied settings and
observed browser routing. Explain permission scope, first-load restart limits,
per-browser checking and conflicts in dialogs rather than permanent paragraphs.
Do not equate a successful helper/registry write with verified routing.

Backend tests precede frontend changes. Cover fixed native command construction,
policy conflicts, atomic file updates, unrelated-policy preservation, independent
restoration, authenticated helper control and lease recovery. Native browser
tests must launch without proxy flags, apply policy and reopen the browser with
the same profile. Use an isolated mount namespace for Linux policy tests, never
the user's `/etc` or existing browser. Add packaged helper self-checks and platform
CI checks. Record native tests actually run; adapter mocks cannot establish
Windows/macOS browser acceptance. Keep unresolved gaps on the board.

Linux native automation verifies policy routing on normal first launch, same
profile relaunch and a different profile without proxy flags. Automated headless
live-refresh IPC in the private mount namespace is not acceptance evidence;
the installed Brave accepted the local refresh command in its existing normal
instance without changing policy files or closing windows. Full live policy
refresh across browser versions still needs platform evidence. Windows/macOS
CI executes real Edge/Chrome launch and relaunch with isolated profiles and real
temporary registry/network configuration, restoring it afterward. This does not
establish Safari or every Firefox/package variant acceptance.

Local implementation verification: full Java 17 verification discovers 330
tests, with 329 passing and the Windows/macOS-only native scenario skipped on
Linux. Native Brave tests cover policy-based normal relaunch and a second
profile, HTTPS/subdomains, focus transitions and shutdown fallback. The existing
native Firefox transport test also passes. Frontend: 123 tests, production build,
privacy audit and lint (two existing SettingsPage warnings). Rust check/format,
both packaged website guardian self-checks and the revised diagram pass.

Windows registry updates, snapshots and normal restoration are batched to avoid
spawning one PowerShell process per key on every heartbeat. A failed batch falls
back to independent restoration. Desktop setup PATCH requests allow time for the
OS permission prompt; ordinary requests retain their shorter timeout. Elevated
policy paths must be root-controlled and new policy files/directories readable
by browser users. Root journals remain private.

## Native CI corrections

Pass Windows connection scripts through UTF-16LE `-EncodedCommand` so JSON and
C# interop quotes survive process invocation. Decode collected scripts before
batching and validate the whole batch before execution. Native connection
commands may include bounded stderr in helper diagnostics; other desktop
queries retain their existing generic error behavior.

Restore macOS's logically empty PAC using networksetup's literal quote-pair
representation, normalize it alongside `(null)` on reads, and restore the saved
enabled flag afterward. Require readback confirmation before reporting disabled;
never discard a restoration journal just to make a test pass.

The private Linux browser test namespace must include `/bin`, required by the
official Debian Brave wrapper, and include browser startup output on failure.
Native runner results remain the acceptance gate for these corrections.

Resolve the Linux browser executable's symlinks before entering the private
namespace: Debian's `/usr/bin/brave-browser` may traverse `/etc/alternatives`,
which intentionally is not exposed by the isolated policy fixture. Native
Windows/macOS probes navigate and verify page content through loopback DevTools,
then request normal browser shutdown before relaunch; screenshot completion is
not a reliable browser-exit signal. Retry macOS packaging at most three times
only for the observed `hdiutil: create failed - Resource busy` failure; other
failures stop immediately and no verification check is skipped.

## Sources

- [Brave platform policies](https://support.brave.app/hc/en-us/articles/360039248271-Group-Policy).
- [Chromium ProxySettings and dynamic refresh](https://chromium.googlesource.com/chromium/src/+/refs/heads/main/components/policy/resources/templates/policy_definitions/Miscellaneous/ProxySettings.yaml).
- [Edge ProxySettings](https://learn.microsoft.com/en-us/deployedge/microsoft-edge-policies/ProxySettings).
- [Firefox proxy policy](https://firefox-admin-docs.mozilla.org/reference/policies/proxy/).
- [Firefox Linux policy locations](https://support.mozilla.org/en-US/kb/customizing-firefox-using-policiesjson).
- [Chromium's local-only refresh switch](https://github.com/chromium/chromium/blob/main/chrome/common/chrome_switches.h).
- [PowerShell encoded command transport](https://learn.microsoft.com/en-us/powershell/module/microsoft.powershell.core/about/about_powershell_exe?view=powershell-5.1).
