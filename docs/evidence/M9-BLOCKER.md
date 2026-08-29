# M9 — Contract Audit Evidence

Status: **BLOCKED — IMPLEMENTATION NOT AUTHORIZED BY THE CURRENT CONTRACT**

Date: 2026-08-29

## Result

M9 was audited against `docs/milestones/SDD-M9-CREDIT-LEDGER.md`, the invariant
catalog, the architecture baseline, and the repository source tree. No complete
financial contract exists from which a safe credit/ledger implementation can be
derived.

The current SDD explicitly says `DRAFT — NOT AUTHORIZED; REQUIRES FINANCIAL
SDD`, leaves the API undefined, and requires approval of precision, reversal,
concurrency, and accounting rules before execution. Its stop condition is to
never invent accounting behavior.

## Missing decisions that block implementation

- monetary precision, scale, rounding, currency, and overflow rules;
- account and ledger-entry model, allowed entry types, sign conventions, and
  balance semantics;
- lifecycle for pending and confirmed entries;
- compensation/reversal command semantics, linkage, and idempotency;
- concurrency/versioning and locking guarantees for balance-affecting writes;
- separation and retention of audit evidence;
- authorized HTTP commands/queries and error contract;
- accounting approval of the resulting financial invariants.

The repository contains only `INV-FIN-001`: confirmed entries cannot be
updated/deleted and corrections compensate. That invariant is necessary but
not sufficient to define the financial domain or its API.

## M10–M12 dependency audit

The next SDDs are also drafts and explicitly stop before implementation:

| Milestone | Missing contract | Current stop condition |
|---|---|---|
| M10 Payments | provider, payment state machine, retry, webhook threat model, API | no provider calls without an approved adapter contract |
| M11 Reconciliation | source formats, matching/tolerance rules, discrepancy policy, operator states | never auto-resolve undefined discrepancies |
| M12 Messaging | channel/provider, consent, purpose, templates, retention, delivery lifecycle | never send without explicit consent semantics |

These are product/domain-contract dependencies, not missing external access
permissions. User authorization to modify the repository cannot substitute for
undefined financial, provider, reconciliation, or consent semantics.

## Verification

- `rg` audit found no additional M9 financial contract or implementation to
  reuse.
- M8 was already integrated into `develop` as merge `52239d3` (PR #13).
- The M8 implementation and full application gates passed before integration;
  no M9 production code was added because the required contract is incomplete.

## Required unblock

Provide and approve the dedicated financial SDD covering the decisions above.
After that contract exists, M9 can be implemented with the required tests for
immutability, compensation, precision, concurrency, rollback, RLS, and
idempotency. M10 must remain paused until M9's financial truth is complete.
