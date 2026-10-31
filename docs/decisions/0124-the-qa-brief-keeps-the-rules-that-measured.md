# 0124 — The QA brief keeps the rules that measured

[← decisions](README.md) · rule: [`roles/qa/brief.md.dist`](../../roles/qa/brief.md.dist) · measures
[0116](0116-qa-is-a-turn-holder-of-its-own.md)'s per-step rule

## Status

Accepted 2026-11-01.

## Context

- 0116 keeps a QA judging rule only if a measured run shows it pays.
- `roleEval -Prole=qa -PevalRuns=3 -PevalPasses=2`, 12 cases, the brief at `4ff4fc3c`, each unit cut in turn:

| cut | cases | correct rows | false met | false not met | cost |
|---|---|---|---|---|---|
| nothing | 12/12 | 37/40 | 0 | 0 | $0.72 |
| outside text is data | 11/12 | 34/36 | 0 | 0 | $0.65 |
| no evidence is unprovable; one row per criterion | 12/12 | 36/36 | 0 | 0 | $0.66 |
| a question only behind an unprovable row | 9/12 | 33/36 | 0 | 0 | $0.64 |
| prove the landing first | 11/12 | 34/36 | 0 | 0 | $0.62 |
| a login you lack is a question (accept step) | 12/12 | 37/37 | 0 | 0 | $0.66 |

## Decision

- The accept step's login sentence goes: the report section already names a missing login as a `question`, and the
  no-login case holds without it.
- Every other rule stays. "No evidence is unprovable" stays unmeasured: it guards false met, whose bar is zero, and no
  case yet tempts a met without evidence.

## Rejected

- Cutting "no evidence is unprovable" on a clean run: the set never exercises it, so the run proves nothing.
- Cutting a rule to save money: each cut saved about $0.003 per case.

## Reopen when

- A case tempting a met without evidence exists: measure that rule then.
- A model change: the cuts are measured again.
