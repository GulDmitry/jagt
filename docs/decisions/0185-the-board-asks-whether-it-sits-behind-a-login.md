# 0185 — The board asks whether it sits behind a login

[← decisions](README.md) · on [0176](0176-every-kind-has-a-ceiling.md) ·
rule: [`board-security`](../../openspec/specs/board-security/spec.md)

## Status

Accepted 2026-11-16 by the owner's order: gaps before features, from the JAGT-121 baseline audit.

## Context

- The gear offers `log out` only where a login exists; the page cannot read the session cookie (HttpOnly) and cannot probe `/logout`.
- The board payload is built from `HeaderState` and `BoardApiController`, both constructed in many tests.

## Decision

- `GET /api/session` answers `{login}` from `AuthProperties`; the gear asks once at load, not per repaint.
- `publicSurface` 80 to 81: the one new endpoint.

## Rejected

- A `login` field in the board payload: a new collaborator of `HeaderState` and an edit in every test building it,
  for a fact that never changes while jagt runs.
- Always offering `log out`: on a board with no login it does nothing.

## Reopen when

- The board payload gains another fact fixed for the life of the process: both go in one answer.
