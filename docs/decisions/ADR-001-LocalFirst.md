# ADR-001: Local-first data and privacy

**Status:** Accepted  
**Decision date:** 2026-11-08

## Context

Focus sessions, application activity and optional future screen captures are
personal data. The product must work offline and should not require an account
or remote service.

## Decision

The application stores and processes productivity data on the user's device.
The core runtime makes no outbound network calls, uses no telemetry or crash
uploader, and exposes its API only on loopback with a per-installation token.
On 2026-11-26 the user approved one explicit exception for FST-050: after
separate consent, a loopback proxy may forward client-requested traffic from
browsers and other apps using temporary system proxy settings to the requested
destination. It makes no autonomous outbound requests. HTTPS stays encrypted, with no telemetry, cloud service
or browsing-payload storage. See [ADR-011](ADR-011-WebsiteProtection.md) for the
enforcement, routing verification and restoration requirements.
Internet access may be used separately to obtain or update the application and
its dependencies; it is not required for product operation.

## Consequences

- Retention, export and delete-all controls operate on local data.
- Desktop and backend integrations must not silently send user data elsewhere.
- Privacy checks include dependency review and a no-non-loopback-network test
  with website protection disabled, plus bounded request-driven proxy tests.
