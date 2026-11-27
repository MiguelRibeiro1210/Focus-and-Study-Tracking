# ADR-016 — Select installed applications (FST-051)

Status: approved for implementation by the user, 2026-11-27.

## Experience and discovery

Application rules start with a searchable checkbox catalog on the current
computer. Show name/source, Already added, Refresh, and App not listed? Add
manually. Multi-add creates enabled Block rules; names/actions remain editable.
Keep manual path/process entry, defaulting to the backend platform, and website
entry. Preserve selections on errors. Use existing generic icons and provide
loading, empty, partial, retry and save-error states.

Windows reads Start-menu shortcuts, App Paths and current-user Store/MSIX
manifests. macOS reads application bundles in system/user application directories.
Linux reads XDG desktop entries and local Flatpak/Snap metadata. Do not enumerate
every process or search the whole disk. Never execute launcher contents, request
administrator access, install apps or contact external services. Bound metadata
sizes, scan depth/count and command time. Resolve native symlinks; never create
automatic targets for shared launchers/interpreters. Unresolved entries are
unselectable with a short reason; healthy providers survive partial failures.

GET /api/v1/applications?refresh=false is authenticated and returns platform,
applications, partial, warnings and scannedAt. Entries have stable id,
displayName, source, nullable typed target, selectable and nullable reason.
Cache for 60 seconds in memory; Refresh rescans. Do not persist the catalog.

## Identity and compatibility

Extend APPLICATION targets with matchKind APPLICATION_ID. Values are exact,
case-preserving literals: windows:<AUMID>, macos:<bundle ID>, flatpak:<app ID>,
snap:<instance.application>. Validate namespace against platform. Extend observed
AppIdentifier with optional applicationId; retain its three-argument constructor
and old snapshot JSON compatibility. Path rules outrank app IDs, then process
names. V12 expands the database constraint; existing rules and exports retain
their meaning.

Windows resolves process AUMIDs and hosted UWP windows to the actual application
PID, never ApplicationFrameHost. macOS uses NSRunningApplication bundle identity.
Linux reads the foreground process's /proc/<pid>/root/.flatpak-info and Snap
kernel confinement metadata. Do not infer a package from a shared launcher or
unverified command line. Missing identity must not produce a claimed match.
Keep existing PID/start-time suspension/restoration safeguards, session
eligibility and desktop support. This does not suspend every process in a package.
Catalog discovery does not imply enforcement permissions are available.

## Atomic addition

POST /api/v1/blocking-rules/batch accepts {rules: [BlockingRuleInput]} with
1–100 complete application rules. Validate all targets and canonical duplicates,
lock the policy once and save in one transaction. Return 201 and saved DTOs;
422 invalid or 409 duplicate saves nothing. Keep all single-rule routes.
The UI merges a successful batch. On a duplicate race, refresh saved rules and
preserve remaining selections. No partial additions or repeated single POSTs.

## Verification and workflow

Test discovery fixtures, malformed/unreadable sources, shared launchers,
symlinks, package isolation, cache/partial scans, identity precedence, old
snapshots, migration/export, authenticated access and atomic rollback first.
Then test search, multi-selection, existing targets, manual fallback, refresh
and failures in the frontend. Require Java 17 verification, frontend checks,
native platform tests and all installer/smoke jobs. Ask before each commit;
push approved commits. Update the board last and keep one story In Progress.

## Sources

- [Windows identities](https://learn.microsoft.com/en-us/windows/configuration/store/find-aumid).
- [Process AUMID API](https://learn.microsoft.com/en-us/windows/win32/api/appmodel/nf-appmodel-getapplicationusermodelid).
- [macOS running application metadata](https://developer.apple.com/documentation/appkit/nsrunningapplication).
- [Flatpak metadata](https://docs.flatpak.org/en/latest/flatpak-command-reference.html).
- [Desktop entry execution syntax](https://specifications.freedesktop.org/desktop-entry-spec/latest/exec-variables.html).
