# 0014 — Behaviour is recorded as OpenSpec specs

[← decisions](README.md) · rule: [`openspec/specs/`](../../openspec/specs/)

## Status

Accepted 2026-10-06.

## Context

A task's plan and decisions ended in `artifacts/<date>-<taskId>/`, outside git and never read back: the next session
in a project saw the code and a commit message, nothing of what was decided. jagt's own rules lived in
`docs/rules/` and `USE-CASES.md`, kept by hand with nothing checking their shape.

## Decision

- What jagt does lives in `openspec/specs/<capability>/spec.md`: a rule is a requirement, a situation a scenario.
  Code conventions stay in `docs/rules/`, the why in `docs/decisions/`.
- In any repository holding `openspec/`, a hand-back at REVIEW_PENDING is refused until the task's change validates
  strict, or says `skip_specs: true` (`HandBack.specsOwed`).
- `ship` folds the change into the main specs before the session commits (`SpecFold`), so the delta travels in the
  request with the code and the base branch stays read-only.
- The CLI is a vendor: only `adapter/OpenSpec` names it, telemetry off.

## Rejected

- The `/opsx:*` commands driving the cycle: a prompted convention, one vendor's, where a refusal is a hard contract.
- Archiving after `deploy`: jagt would have to write the task branch itself.
- Gating PLAN_PENDING: a session in plan mode cannot write the change.
- Specs without code anchors, as OpenSpec advises: the anchor is how a reader finds the enforcer.

## Reopen when

- Main specs drift from the code faster than `docs/rules/` did, measured by a review round finding a stale one.
- Sessions spend more turns satisfying the validator than writing the change.
