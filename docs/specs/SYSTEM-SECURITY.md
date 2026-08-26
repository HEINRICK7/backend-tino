# System Specification — Security

Status: **APPROVED CONTRACT; IMPLEMENTATION STARTS M2**

## Controls

- Validate JWT issuer, signature, expiry, and intended audience/authorized party according to the Keycloak client contract.
- Default deny; only documented health/OpenAPI endpoints may be public.
- Resolve tenant authority through membership and enforce it again with RLS.
- Reject inactive users, memberships, revoked devices, and malformed identifiers without leaking tenant existence.
- Never log tokens, credentials, sensitive payloads, or personal data. Correlation IDs are untrusted input and must be sanitized/bounded.
- Separate auditable security/domain events from technical logs.
- Secrets come from runtime configuration, never committed defaults for production.

## Threat-driven tests

M2 tests invalid/expired/wrong-issuer JWT and security defaults. M3 tests cross-tenant membership. M4 tests device spoofing. M6/M7 test tenant substitution in envelopes and cursors. Production permit-all behavior is prohibited.
