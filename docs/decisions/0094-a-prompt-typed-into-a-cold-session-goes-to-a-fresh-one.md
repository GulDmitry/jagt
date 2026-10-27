# 0094 — A prompt typed into a cold session goes to a fresh one

[← decisions](README.md) · extends [0001](0001-a-task-session-lives-one-round.md) · rule: [`runtime`](../../openspec/specs/runtime/spec.md)

Superseded in part by [0097](0097-idle-is-read-from-the-session-transcript.md).

## Status

Accepted 2026-10-27.

## Context

0001 starts a fresh session for instructions jagt relays. A prompt typed in the task's window after the 1h cache
expired bypassed it: the CLI rewrote the whole context uncached, about 2% of a session limit at hundreds of k tokens.

## Decision

- `UserPromptSubmit` is answered like `PreToolUse` and `Stop`. Idle is counted from the turn's end jagt recorded,
  not the transcript: the log may already hold the new prompt.
- Past `orchestrator.agent.cacheLifetime` (default 1h, `0` off) the prompt is persisted under the brief, a fresh
  window is launched, and only then the old prompt is blocked. Any failure passes it through: a message is never lost.
- A slash command, a pasted image, a session no task owns and a session that was working pass untouched.
- What the old session knew rides in `task_context.md` below the prompt, gathered without a model: last report,
  commits, changed files, the tail of the conversation's text, the transcript's path. The context file expires on the
  next relay; a start prompt or a second file would outlive its round.
- `task_notes.md` is kept fresh by the existing turn-end refusal, once when older than `notesEvery` (30m), because a
  cold prompt cannot ask the old session for notes.

## Rejected

- A cache-warming ping: pays the rewrite every hour to save it once.
- Carrying the tail in the start prompt: the tmux command message is small, and the prompt outlives its round.

## Reopen when

- A fresh session's first round costs more than the cold conversation it replaced.
- The old session's last words prove too little to continue from, so the tail's cap is wrong.
