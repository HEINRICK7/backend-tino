# TINO Backend SDD

Status: **BASELINE APPROVED; ONLY M0 AUTHORIZED**

## Document hierarchy

1. [`architecture/ARCHITECTURE-BASELINE.md`](architecture/ARCHITECTURE-BASELINE.md) — approved cross-cutting decisions.
2. [`architecture/adr/`](architecture/adr/) — context, consequences, rejected alternatives, and reconsideration conditions.
3. [`discovery/TINO-ANDROID-BACKEND-DISCOVERY.md`](discovery/TINO-ANDROID-BACKEND-DISCOVERY.md) — observed client constraints.
4. [`specs/`](specs/) — executable system contracts.
5. [`milestones/`](milestones/) — bounded increments; authorization never carries to the next milestone.
6. [`evidence/`](evidence/) — objective proof.

## Execution protocol

- Read the baseline, relevant ADRs/specs, and exactly one milestone before editing.
- Report requirement coverage before editing.
- Implement only that milestone's `IN SCOPE`; `OUT OF SCOPE` is a hard boundary.
- Remove any partial file, migration, endpoint, entity, or behavior belonging to a later milestone.
- Run every acceptance criterion and required test.
- Mark a criterion `PASS` only with objective evidence. Missing evidence means `BLOCKED`.
- Stop after producing milestone evidence. A passing milestone never authorizes the next one.

Permanent proof obligations are catalogued in [`specs/INVARIANT-CATALOG.md`](specs/INVARIANT-CATALOG.md).
