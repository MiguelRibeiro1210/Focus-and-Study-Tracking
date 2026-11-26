# ADR-008 — Saved distraction rule screen

**Status:** Agreed delivery scope for FST-049, following FST-048.

## Design review and behavior

Keep the existing boundaries banner, filter/search toolbar, rule list and
sidebar. Replace placeholder rows with the persisted local API from
[ADR-007](ADR-007-BlockingRuleManagement.md). Use explicit action labels and
an Enabled switch; neither claims that protection is currently active.

An inline form supports creation and editing with a display name, target type,
action (Log, Warn, Block), enabled flag and category OTHER. Website forms accept
a domain, explain subdomain matching and reject URL syntax before submission.
Application forms collect Windows/macOS/Linux, executable path or process name,
and the identifier. The backend remains authoritative for normalization,
platform path validation and canonical duplicate detection. Changing target type
replaces the target as a whole. Window-title matching remains unavailable.

Load on entry, expose loading and empty states and retry failed initial loads.
Search names and target identifiers case-insensitively and combine with the
All/Websites/Apps filter. Display platform and match kind for application rules.
An unfiltered empty policy differs from a filter with no matches.

Apply create/edit/toggle/delete results only after server success. Disable
mutations while requests are pending and preserve the old rows and form values
when saving fails. Present backend validation/conflict details near the form.
Require an explicit inline confirmation before deletion; cancellation preserves
the rule. Restore list focus after form dismissal and focus the first field on
opening. Use labelled native controls and accessible status/error messages.

The sidebar explains that saved configuration is available but runtime
protection is not connected in this story. Future FST-027/FST-028/FST-050 must
replace this with measured capability/permission and session activity state.
Focus profiles remain unavailable; one installation policy is supported.

## API and verification

Use the existing browser proxy/native transport selection. Strictly validate
successful rule payloads before replacing displayed data; preserve API error
messages and details, including 409 duplicates, 422 targets and 404 stale edits.
Do not put the local bearer token into UI state or local storage.

The backend contract and meaningful backend tests already pass in FST-048;
this story adds no backend behavior. Add frontend tests for typed API requests,
invalid responses, CRUD, target-specific forms, filters/search, initial retries,
failed saves/toggles/deletes, confirmation and pending controls. Run frontend
Vitest, build and lint; inspect the final diff before updating the board.
The existing management sequence/SSD flows remain unchanged.
