# 0115 — A session is one stored record, read from typed signs

[← decisions](README.md) · rule: [`attention`](../../openspec/specs/attention/spec.md), [`task-flow`](../../openspec/specs/task-flow/spec.md)

## Status

Accepted 2026-10-30. Extends [0110](0110-the-task-statechart-runs-on-an-engine-of-our-own.md) with a session region.

## Context

- Whether a session lived, worked, asked, had stopped or was cold was answered by four memories: the probe's maps,
  a silence stamp on the task, a transcript read, and a keep-alive stamp. They disagreed after a restart and at the edges.

## Decision

- One stored record, `task/Session`, and a region `SessionPhase` (NONE, WORKING, IDLE, QUIET, FAILED, GONE) derived from it.
- Only `TaskReducer` writes it, from `SessionSign`s stamped with jagt's clock (`FlowDoorsTest`); a read is one more sign.
- Precedence, first sign that answers wins, a tie kept by a halt and lost to the start of work: an end or a failure at once;
  a turn end then a stale read is QUIET; any later sign of life drops a halt; an unreadable sign is no verdict.
- Asking is no phase: a hook's blocking text or the trust dialog stamps the report PERMISSION, which work clears.
- D1 a quiet session whose process runs is yours; D2 a call ends a gone or failed halt; D3 one channel for asking;
  D4 quiet and gone at every open status, the banner only where watched; D5 a dead window is gone at every status;
  D6 a tool use is a sign of life; D7 every sign persists; D8 an unknown turn start lets a turn end.
- A fresh session starts the record over; the log a session names is a sign, `SessionProbe` keeps no memory.

## Rejected

- Memory-only signs: each restart re-decided from nothing.
- One more clock for cold prompts: idle counts from the record's `idleSince`.

## Reopen when

- A runtime emits a sign the table has no row for, or a restart is seen to read a session wrongly from its record.
