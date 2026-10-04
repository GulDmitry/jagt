# 0009 — What jagt can read, it reads once and quotes

[← decisions](README.md) · rule: [`seams.md`](../rules/seams.md#every-assistant-call-is-metered)

## Status

Accepted 2026-10-04.

## Context

An audit after [a report on token waste in coding agents](https://habr.com/ru/articles/1089110/): 38 KB of test
output carried one fact, "178 passed", and rules that each closed a real error together paid for redundant work.
Three of its five questions hit jagt:

- `list_tasks` returned `state.json` pretty-printed: 7.7 KB for one task, 56% `history` and 27% `agentSpend`.
  At the 24-task cap one call is ~185 KB, ~50k tokens, to answer "what is open".
- Each role of the Master panel read the round's diff itself: five roles, five `git diff` explorations of
  the same worktree.
- Every sub-agent's brief carried every configured project and a snapshot of every task, stale on arrival and
  unusable under rule 2 (never touch another worktree).

## Decision

- `list_tasks` returns one line per task: id, status, title, message, each worktree with its request.
- `WorktreeChanges.diff` reads the round once — committed and not since the base, new files by name — and every
  role's prompt quotes it, the round before the role, so the roles after the first read it from cache.
- A diff over 100,000 characters is not quoted; a repository git cannot read is named, never left silent.
- The worker's brief names no other project and no other task.

## Rejected

- A model summarising tool output: the report's own finding — a parser is exact, a model guesses. jagt computes
  the projection itself (0006).
- The diff in the cached system prompt: it names no task, so it stays cached across every round.
- Dropping `history` from `state.json`: the board's timeline reads it; only the prompt does not need it.

## Reopen when

- A Master role misses a fact because it trusted the quoted diff over the worktree (a file outside git's view).
- Metering per MCP tool shows another result past ~10 KB per call.
