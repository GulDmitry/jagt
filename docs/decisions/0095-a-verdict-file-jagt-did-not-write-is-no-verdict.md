# 0095 — A verdict file jagt did not write is no verdict

[← decisions](README.md) · extends [0044](0044-a-session-writes-only-inside-its-task.md) · rule: [`master`](../../openspec/specs/master/spec.md)

## Status

Accepted 2026-10-27.

## Context

A session has Edit and Bash over its worktree, and the verdict lives in `.jagt/master-review.json` there. A probe wrote
`{"round":<turn id>,"reads":[]}` with the turn id read from `GET /state`: no findings, so ready, no role asked, and in
`act` the Master shipped it.

## Decision

- `MasterReview`, the record's one writer, stores the SHA-256 of the exact bytes as a mark of the task's current `Turn`
  in `state.json`, then writes the file: a reader never meets a verdict without its seal. The verdict reads back only
  where the file hashes to that mark.
- A mismatch or a missing mark logs a WARN and reads as no verdict: the Master reads the round afresh.
- The mark sits on `Turn`, not `TaskState`: `marks` is the bounded, jagt-written store that survives relays, and
  `TaskState` is a positional record whose every field costs a reader and a creator.
- A review no role read is a question for the human, never ready: an empty round was always a hole.

## Rejected

- Moving the record out of the worktree: a second store with its own cleanup, for a guarantee the hash gives.
- A deny rule on `.jagt/**`: a shell redirect passes it.

## Reopen when

- Sessions run as another user, so the worktree and `state.json` differ in who may write.
- `state.json` becomes writable by a session, so the mark can be forged beside the file.
