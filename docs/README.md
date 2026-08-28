# TINO Backend SDD

Status: **BASELINE APPROVED; M5 IMPLEMENTATION PASS; M6 NOT AUTHORIZED**

## Document hierarchy

1. [`security/SECURITY-GIT-SAFETY-POLICY.md`](security/SECURITY-GIT-SAFETY-POLICY.md) — mandatory security and Git safety rules for every change and milestone.
2. [`architecture/ARCHITECTURE-BASELINE.md`](architecture/ARCHITECTURE-BASELINE.md) — approved cross-cutting decisions.
3. [`architecture/adr/`](architecture/adr/) — context, consequences, rejected alternatives, and reconsideration conditions.
4. [`discovery/TINO-ANDROID-BACKEND-DISCOVERY.md`](discovery/TINO-ANDROID-BACKEND-DISCOVERY.md) — observed client constraints.
5. [`specs/`](specs/) — executable system contracts.
6. [`milestones/`](milestones/) — bounded increments; authorization never carries to the next milestone.
7. [`evidence/`](evidence/) — objective proof.

## Execution protocol

- Read the mandatory Security & Git Safety Policy, baseline, relevant ADRs/specs, and exactly one milestone before editing.
- Report requirement coverage before editing.
- Implement only that milestone's `IN SCOPE`; `OUT OF SCOPE` is a hard boundary.
- Remove any partial file, migration, endpoint, entity, or behavior belonging to a later milestone.
- Run every acceptance criterion and required test.
- Mark a criterion `PASS` only with objective evidence. Missing evidence means `BLOCKED`.
- Stop after producing milestone evidence. A passing milestone never authorizes the next one.

Permanent proof obligations are catalogued in [`specs/INVARIANT-CATALOG.md`](specs/INVARIANT-CATALOG.md).
