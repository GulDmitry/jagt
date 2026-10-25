# 0091 — The shipped Master reads three roles

[← decisions](README.md) · rule: [`master-brief.md.dist`](../../master-brief.md.dist) · commit `2784fe9f`

## Status

Accepted 2026-10-25, recorded retroactively (what commit `2784fe9f` proves).

## Context

- One Master round is one model call per role.
- The shipped Master read the author's 5 self_review roles.
- One real install spent $25.77 on 159 Master calls in a day.
- masterEval 13/13 on sonnet with 3 roles (2026-10-25).

## Decision

- `master-brief.md.dist` carries its own table of 3 roles.
- architect carries the chaplain's question: should this exist at the scope asked.
- QA is the test question.
- developer carries the designer's question for UI files.
- The session's own self-review keeps its 5 roles.

## Rejected

- A designer row behind an `applies` column: the owner wants exactly three.
- One call for all roles: unproven; measured later.

## Reopen when

- masterEval shows a verdict the dropped roles caught.
