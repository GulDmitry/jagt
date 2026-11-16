# 0178 — An MCP config file is read by its own job, and the image seeds the agent's folder

[← decisions](README.md) · on [0177](0177-a-missing-project-program-is-read-by-its-own-job.md)
Rule: AGENTS.md "jagt's own reach failing is the board's, never a card's"

## Status

Accepted 2026-11-16, unasked; raises `startTriggers` from 54 to 55.

## Context

- A config file mounted from a Secret may be absent; every session and read then lacks its tracker, late and per task.
- The mark must end on the read that finds the file, so the read repeats while jagt runs.
- HOME on the volume hides what the image bakes under it.

## Decision

- `McpConfigReach` is a `Job`, every minute, marking a missing or unreadable file in the reach ledger.
- A value starting with `{` is the JSON itself and is not a file.
- `entrypoint.sh` copies `JAGT_AGENT_SEED` over `JAGT_AGENT_HOME` on every start; the organisation's image sets both, jagt names no folder.

## Rejected

- A startup check refusing the start: one absent file would stop every project.
- Folding into `ProjectProgramsReach`: it reads programs, not files.
- Seeding once: a new image would not reach a volume that already holds the old plugins.

## Reopens when

- A third config file kind needs the same read: one job reads all of them.
