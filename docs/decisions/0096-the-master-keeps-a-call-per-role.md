# 0096 — The Master keeps a call per role

[← decisions](README.md) · rule: [`master-brief.md.dist`](../../master-brief.md.dist) · measures 0091's rejected row

## Status

Accepted 2026-10-27.

## Context

- 0091 left one call for all roles unproven.
- `masterEval` 2026-10-27, 15 cases, `-PevalRuns=3`, the shipped brief against one `reviewer` row asking all three
  roles' questions, the rest of the brief byte for byte:

| brief | cases held | runs | cost | time |
|---|---|---|---|---|
| three roles | 15/15 | 30 | $4.50 | 831 s |
| one folded role | 14/15 | 32 | $2.64 | 576 s |

- The folded role failed "a fix nothing tests" in 2 of 3 runs: it named the missing test `unguarded` and passed.
- A plan round reads one role in both, and cost the same: the saving is the panel's alone.

## Decision

- `master-brief.md.dist` keeps one row, one call, per role.

## Rejected

- One folded role: 41% cheaper, but a test question asked among others loses its severity.

## Reopen when

- A folded brief holds every `masterEval` case over three runs.
