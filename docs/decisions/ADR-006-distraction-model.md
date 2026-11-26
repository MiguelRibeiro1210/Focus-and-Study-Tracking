# ADR-006 — Distraction model documentation review

Status: documentation, backend tests and the completed domain implementation
reviewed and approved by the user on 2026-11-26. FST-008 is complete, with all
224 backend tests passing. Follow-on stories require separate authorization.

## Agreed scope

- Website rules match the configured domain and its subdomains.
- Application rules initially match an executable path or a process name.
  Window-title patterns are deferred; a browser title does not identify a domain.
- One active blocking policy owns configured rules. Logs record observations
  and outcomes separately from the policy.
- FST-008 defines domain behavior, ports and event/read-model contracts.
  FST-048 owns storage/API, FST-049 the UI, FST-027 application detection,
  FST-028 application enforcement, FST-050 website integration and FST-037
  log persistence/projection.

## Approved model

| Concept | Responsibility |
|---|---|
| `BlockingPolicy` | Aggregate owning rules and evaluating enabled matches. |
| `BlockingRule` | Entity with ID, display name, typed target, category, action and enabled state. |
| `ApplicationTarget` | Value object with OS platform, match kind (`EXECUTABLE_PATH` or `PROCESS_NAME`) and configured value. |
| `WebsiteTarget` | Value object with normalized domain; subdomains are always included in this version. |
| `ObservedDistraction` | Application identity or observed website host, plus transient adapter context. |
| `BlockingDecision` | Matching rule ID, target, category and chosen action, or no match. |
| `BlockingPolicyRepository` | Domain port for loading the active policy and saving changes. |
| `DistractionLog` | Separate read model correlated by detection ID; never the source of configured rules. |

Rules support `LOG`, `WARN` and `BLOCK`, as already described by UC-12.
Disabled rules cannot match. An empty/missing policy produces no detection or
block. Policy evaluation is pure and must not read files, inspect processes,
resolve DNS, call browsers or write logs. Platform adapters supply observations.

### Approved matching and validation defaults

- A display name is trimmed and required. Target values are required and typed;
  executable paths must be absolute for the declared OS, and process names must
  be a single executable name, not a path or wildcard expression.
- Paths are normalized lexically, without requiring the app to exist on disk.
  Windows identifiers compare without case; Linux/macOS identifiers compare
  with case. Preserve entered values for display; use canonical values for
  matching and duplicate checks. OS metadata avoids interpreting Windows paths
  using the host's Linux/macOS path syntax.
- Website configuration accepts a bare DNS domain, not a URL, path, port,
  wildcard or IP address. Normalize case, internationalized names to ASCII and
  a terminal DNS dot. The future UI can explain the required domain format.
- `youtube.com` matches itself, `www.youtube.com` and
  `music.youtube.com`. It does not match `notyoutube.com`,
  `youtube.com.example.org` or a URL merely containing that text.
- Website comparison is exact host equality or a suffix preceded by `.`.
  Host information must come from a supported website adapter, never a window
  title. Do not retain URL query strings, credentials or page contents.
- Duplicate keys are `(platform, matchKind, canonicalValue)` for applications
  and canonical domain for websites, within the policy. Changing the action,
  display name or enabled state does not create another rule for that target.
- App path rules take precedence over app process-name rules. Website rules
  select the longest matching domain (the most specific host suffix).
  There is no insertion-order tie because duplicate target keys are rejected.
- Application and website evaluations are independent. Blocking the browser
  application intentionally affects that whole application; a website rule
  must affect only its matched site. `LOG`/`WARN` are not allow-list overrides
  for a separate matching application rule.
- Monitoring and enforcement require both session `RUNNING` and interval type
  `WORK`. Check again immediately before enforcement; pause, break, stop or a
  disabled/deleted rule cancels a stale decision.

## Detection, enforcement and events

Application adapters report executable path/process name and a transient
process handle/identity suitable for the platform blocker. The browser/website
adapter reports an actual host and transient page/request context. A process
name alone must not authorize blocking every process with that name: resolve
and revalidate the detected instance before enforcement.

`DistractionDetected` carries event ID, detection ID, session ID, rule ID,
configured/observed target snapshot, category, selected action and occurrence
time. `DistractionBlocked` and `DistractionBlockFailed` carry their own event
ID, the same detection ID, session ID, target snapshot, occurrence time and
outcome/failure reason. Rule edits/deletion must not rewrite historical facts.
Raw OS handles, browser credentials and full browsing URLs are not durable event
payloads. Log consumers correlate and deduplicate events by their IDs.

A detection event commits before acting. The use case then rechecks eligibility
and invokes the platform/website adapter. Success is recorded only after the
adapter confirms enforcement; failures produce a reason and learner feedback.
An OS/browser operation cannot be rolled back by a database transaction, so
never describe the combined operation as atomic or blindly replay an outbox
notification as another OS block command. Outcome projections and WebSocket
notifications run after the corresponding event transaction commits.

`LOG` produces a detection only; `WARN` additionally requests learner feedback;
`BLOCK` requests enforcement and records its outcome. Repeated ticks use the
existing 30-second detection debounce keyed by session, rule and observed
application instance/website host. Debounce suppresses duplicate logging, not
protection of a new process or website request.

The log keeps append-only source events and an idempotent derived view of each
detection/outcome. Out-of-order outcome delivery must not discard a later
arriving detection. No successful detection is fabricated when integration
cannot observe a target; capability failures remain explicit diagnostics.

## Website integration boundary — FST-050

The diagrams describe ports and intended behavior, not an implemented browser
extension, DNS proxy, hosts-file change or network filter. FST-050 must choose
and obtain approval for the concrete mechanism, browser/OS support, permissions,
local transport and cleanup behavior. No website enforcement mechanism is
selected by FST-008. Restrictions introduced by the chosen mechanism must be
released when focus protection ends; unsupported integration is shown clearly.

## Frontend design review

The existing Distractions screen already has Apps/Websites filters, names,
targets and enable switches. FST-049 will add target-specific forms and display
validation/errors and actual protection availability. The current `BLOCK`
column does not represent the full `LOG`/`WARN`/`BLOCK` action model; replace it
with explicit action and enabled controls when that UI story is implemented.
The Focus profiles card remains unavailable for now; this story does not enable
multiple policies. No frontend source changes are part of this stage.

## Review artifacts and future verification

- [Backend verification](../testing/TestStrategy.md#distraction-domain-verification-fst-008):
  five test classes now execute 92 FST-008 cases. Full Maven verification passes
  all 224 backend tests and packages the application. The implementation includes
  immutable rules/decisions, a repository port and a separate immutable log view;
  actual storage, detection and enforcement remain follow-on work.
- [Domain model](../DM/DomainModel.md) and [domain diagram](../DM/DM.puml).
- [Application detection](../SD/SD_DetectDistraction.puml),
  [application enforcement](../SD/SD_BlockDistraction.puml),
  [website detection/enforcement](../SD/SD_DetectAndBlockWebsite.puml).
- [Rule management](../SD/SD_ManageBlockingRules.puml) and
  [protection cleanup](../SD/SD_ReleaseDistractionProtection.puml).

The user approved documentation, backend tests, backend implementation and its
final review in separate steps. Do not infer approval of follow-on stories.
Meaningful cases include target validation/normalization, domain boundaries,
duplicate rejection on add/edit, path/process precedence, domain specificity,
disabled/empty policies, pure evaluation, session-state eligibility and event
correlation. Do not test trivial ID holders. Persistence, HTTP, browser and OS
integration tests belong to their implementation stories.
