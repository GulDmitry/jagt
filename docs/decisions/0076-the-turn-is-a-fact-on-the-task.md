# 0076 — The turn is a fact on the task

[← decisions](README.md) · rule: [`cases`](../../openspec/specs/cases/spec.md) · step 2 of
[0072](0072-the-conversation-turn-is-the-machines.md) · supersedes in part:
[0050](0050-a-session-stopped-on-a-full-window-is-woken-once.md) (where a wake is kept)

## Status

Accepted 2026-10-24.

## Context

- Whose move it was got re-derived in `Move.ownerOf` and in each Master job's own filter.
- "Already done" lived in memory (`DeployCheckJob.asked`, `MasterAnswerJob.unanswered`, the Master's `pressed` and
  `left` maps) or in `task_context.md`, which every relay overwrites (`AgentSessions.relayIfChanged`).
- After a restart the Master pressed deploy on a deployed task and the session heard "is deployed" again (B12); the
  Master answered a question the session had moved past and set `IN_PROGRESS` on a task at work (C8).

## Decision

- `task/Turn` lives on `TaskState` in `state.json`: holder, the step that opened it, an id, the digest of its facts,
  `since`, and one mark per step for what that step last carried. Absent reads as no turn.
- `flow/Turns.next(status, report, profile)` is the one answer to who holds the turn; `Move.ownerOf` maps it.
- `StepRelay` opens the session's turn; the session's report closes it, naming the next holder. The same step with
  the same facts while that turn is open sends nothing.
- The deploy check, the Master's deploy and repository presses, a step left to the human, an unanswered question and a
  pending wake are marks on the turn. The Master's answer is dropped when the turn moved while it read.
- The holder is written when a relay opens or a report closes the turn; between, the jobs ask `Turns.next`.

## Rejected

- Persisting the in-memory maps as files beside `state.json`: one more file per question, and two writers again.
- Writing the holder on every status move: the doors would need the Master's profile, which `flow/` does not read.
- Keying the relay dedup on the full text: an edited brief would re-send every standing step after a restart.

## Reopen when

- A job needs the holder between a relay and a report, and `Turns.next` answers differently from the turn on file.
