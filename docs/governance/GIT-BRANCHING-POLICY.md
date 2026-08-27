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
