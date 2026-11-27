# ADR-013 — Blocking settings and session-start guidance

**Status:** Approved by the user, 2026-11-27. FST-050 interface follow-up.

## Decision

Move app/website protection switches, connection selection and Brave/browser
setup from Distractions to a Blocking section in Settings. Keep Distractions
focused on rule editing and observation history, with a short link to Settings.
Use short labels and honest status text, not persistent explanatory paragraphs,
raw diagnostics or command lines. Explain options in contextual dialogs. Consent
is still explicit before enabling suspension or forwarding; setup never silently
accepts permission or closes browsers. Explain Brave's voluntary 60-second exit
and current-profile behavior in its confirmation dialog. Keep required browser
verification and recovery actions accessible from the compact Settings controls.

Before each new session start, validate the project/timer inputs, then freshly
read saved rules and the existing protection APIs. Only enabled BLOCK rules
require setup; LOG, WARN and disabled rules do not. App readiness requires opted-in
protection, enabled monitoring, available detection and READY/ACTIVE suspension;
zero currently suspended apps is normal before a session. Website readiness
requires opted-in protection, enabled runtime, VERIFIED routing and no pending
browser launch. Missing/unavailable/unknown status is not treated as ready.
No rules means no prompt. Failed rule/status reads or a five-second check timeout
produce a plain-language uncertainty message, with the same explicit choices.

If relevant protection is not ready, show an accessible modal before creating
the session: **Set up blocking**, **Start without blocking**, **Cancel**. The user
explicitly selected allowing unprotected starts. Explain only the missing app
or website setup in short sentences; give no guarantee that every browser
profile is covered. Continuing starts the validated session once and does not
change saved rules/consent; any working protection remains enabled. Cancel or
Escape does not start anything. Set up blocking opens Settings without starting;
preserve project name and timer overrides and provide a return-to-session action.
Returning does not auto-start: a new Start click rechecks current settings.

Keep the timer draft in app memory across page changes. Prevent duplicate starts
while checking or starting. Ignore stale responses after navigation/unmount.
Modal focus is trapped, Escape cancels, initial focus goes to the safe setup or
cancel action and dismissal restores focus. Existing session pause/resume and
interval transitions are not intercepted by the new-session setup prompt.

## Contracts and verification

Reuse blocking-rules, blocking-status and website-protection APIs. No backend,
domain, persistence, permission or enforcement contract changes are needed.
Backend behavior is already implemented; this change adds meaningful frontend
coverage for readiness decisions, API failures/timeouts, modal choices, draft
preservation, concise settings, consent and browser setup. Verify no setup controls
remain on Distractions, and no pending check creates a session automatically.
Run frontend tests/build/lint/privacy checks and inspect the diff; update the
board last. FST-050 stays open for its unrelated native-platform acceptance gaps.
