# 0010 — The human's own word stands over the Master

[← decisions](README.md) · rule: [`master`](../../openspec/specs/master/spec.md)

## Status

Accepted 2026-10-05.

## Context

A task whose ticket held one line asked "rename the field everywhere?". The Master, answering in the human's
stead, said no, and `master-decisions.md` recorded it as binding. The human then typed "do it" into the
session's window; the session renamed. The next review read only the ticket, the diff and the decisions, called
the rename "settled decision overridden", and had it reverted. The empty round then looped 25 times, ~$106 of
agent spend, before a human stopped it.

The human's words were on disk the whole time, in the agent CLI's own transcript, glued to jagt's nudge where it
was typed into the window while they composed.

## Decision

- Every Master read — each role of a review and every answer in the human's stead — quotes what the human typed
  into the session, `AgentRuntime.humanSaid`, read by the adapter from the CLI's transcript at no model cost.
- jagt's own lines are cut out of it exactly: the launch prompts and the nudge, constants jagt knows.
- Their word stands over anything decided in their stead; earlier decisions bind only where it is silent.
- A transcript that cannot be read stops the round as "could not read", asking no model (0008).

## Rejected

- Asking the human to restate in `task_context.md` what they typed: the window is where they speak; a second
  channel for the same words is two controls for one question.
- A model summarising the session: the transcript is exact and free, a summary guesses (0006, 0009).

## Reopen when

- A runtime other than Claude drives sessions under `master.mode`: its `humanSaid` answers unreadable until it
  reads its own transcript, so every round it reviews stops.
