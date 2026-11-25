# 0242 — A credential check is read by the programs job

[← decisions](README.md) · on [0177](0177-a-missing-project-program-is-read-by-its-own-job.md)
Rule: AGENTS.md "jagt's own reach failing is the board's, never a card's"

## Status

Accepted 2026-11-24, unasked; raises no ceiling.

## Context

- A task session stopped on an npm E401: the project's registry token had expired.
- 0177 reopens when a second per-project read appears: one job reads both.
- `start-triggers` is at its ceiling; a new job would raise it.

## Decision

- A project's `credentialChecks` are read-only commands; jagt reads their exit code alone, never a credential.
- `ProjectProgramsReach` runs them through `CredentialChecks`: at start, daily once passing, each minute while failing.
- A failing check is a reach mark in the board's head; the card of a project's session-less task carries a note, never a stop.
- `AgentWindows.openTab`, the one door of a session start, runs the project's checks first and refuses on a failure.

## Rejected

- A startup check: it reaches the network, which a start never does.
- A second job: it raises `start-triggers`.
- A card stop: a lost reach stops no task; the note ends on the read that passes.

## Reopens when

- A check needs more than an exit code to say what failed.
