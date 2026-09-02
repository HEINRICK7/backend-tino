# TINO Git Branching Governance

## Status

**MANDATORY**

This policy defines the official promotion flow for the repository. It
complements the mandatory [Security & Git Safety Policy](../security/SECURITY-GIT-SAFETY-POLICY.md),
which remains authoritative for secrets, protected history, force-pushes, and
security gates.

## Official flow

```text
feature/* → develop → staging → main
```

- New work starts on a `feature/*` branch based on the current `develop`.
- `feature/*` must never be promoted directly to `main`.
- `develop` must never be promoted directly to `main`.
- Every promotion between environments is performed through a reviewed pull
  request. Required CI, security, test, and architecture gates must pass.
- `staging` is the sole normal source for promotion to `main`.
- Promotion from `staging` to `main` always requires explicit human
  authorization. A passing build does not constitute that authorization.

## Promotion Authority

Luna may create and monitor promotion pull requests, but Luna must never infer
promotion authority from a green check, an open PR, a reviewer comment, or the
absence of a failure. Every promotion requires explicit human authorization.
Promotion to `main` is never automatic.

### Feature to develop

A `feature/*` branch may be promoted to `develop` only when the milestone or
feature status is PASS, all required tests and security checks pass, the
evidence is complete, scope leakage is NONE, and a human explicitly authorizes
the promotion. Once those conditions and the PR checks are green, report
exactly:

```text
FEATURE READY FOR DEVELOP
```

Then STOP and ask the human: “Authorize merging `feature/*` into `develop`?”
Do not merge based on the readiness message alone.

### Develop to staging

`develop` may be promoted to `staging` only when the develop integration build,
regression tests, migrations, security checks, and GitGuardian checks pass with
no blockers, and a human explicitly authorizes the promotion. Once those
conditions and the PR checks are green, report exactly:

```text
DEVELOP READY FOR STAGING
```

Then STOP and ask the human: “Authorize promoting `develop` into `staging`?”
Do not create or merge this promotion without that authorization.

### Staging to main

`staging` may be promoted to `main` only when staging validation and
homologation, smoke and regression tests, and security checks pass, the release
evidence is complete, and a human explicitly approves the production release.
Main promotion is NEVER automatic. Once those conditions and the PR checks are
green, report exactly:

```text
STAGING READY FOR PRODUCTION
```

Then STOP and ask the human: “Explicitly approve promoting `staging` into
`main` for production?” A passing build, prior authorization, or scheduled
workflow is not production approval.

Operational authorization examples:

1. “Authorize merging `feature/m2-identity-security` into `develop` now that
   M2 evidence, tests, security, and scope checks are PASS?”
2. “Authorize promoting the current `develop` HEAD into `staging` after the
   integration, regression, migration, security, and GitGuardian checks pass?”
3. “Explicitly approve promoting the validated `staging` release into `main`
   for production?”

## Protected branch rules

- `main`, `staging`, and `develop` are integration branches, not personal
  work branches.
- Do not force-push, rewrite, reset, or delete these branches.
- Do not bypass the pull-request flow with direct feature/develop-to-main
  updates.
- A conflict, divergent remote state, or need for an exception is a stop
  condition requiring a human decision under the Security & Git Safety Policy.

## Milestone branch rule

The future M2 implementation branch is:

```text
feature/m2-identity-security
```

It must be based on `develop` at the time M2 is explicitly authorized. This
branch-flow initialization does not resume or implement M2; M2 is paused for
this task. No M3 work is authorized by this policy or by branch creation.

## Promotion checklist

Before opening or approving a promotion pull request, confirm:

1. the source branch is the immediately preceding branch in the official flow;
2. the target branch has no unexpected divergence;
3. required build, test, architecture, migration, and security gates pass;
4. the diff contains only the authorized scope;
5. no credentials or generated artifacts are present; and
6. the required reviewer and, for `main`, explicit human authorization are
   recorded.

This policy is permanent repository governance. A future milestone or feature
cannot weaken it implicitly; a change requires an explicitly reviewed policy
revision and human authorization.
