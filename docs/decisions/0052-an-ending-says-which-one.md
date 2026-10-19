# 0052 — An ending says which one

[← decisions](README.md) · rule: [`seams.md`](../rules/seams.md#an-ending-says-which-one) ·
spec: [`attention`](../../openspec/specs/attention/spec.md)

## Status

Accepted 2026-10-19.

## Context

- Claude Code ends a turn with `Stop`, a failed one with `StopFailure` naming its `error`, a session with
  `SessionEnd` naming its `reason`; an interrupt fires none.
- jagt matched `StopFailure` on `rate_limit` alone: an overloaded or unauthorised turn reached the board five
  minutes late as "nothing has moved in its session".
- `SessionEnd` was read as gone whatever its reason: a `/clear` pinged the human "agent stopped".
- AG-UI's `RUN_FINISHED` carries `outcome` as `success`, `interrupt` or `cancelled`, apart from `RUN_ERROR`.

## Decision

- An ending is finished, stopped, failed or gone, and every state, surface and log line keeps them apart.
- A failure is a verdict at once, carrying its source's own word for the cause.
- A hook subscribes to every ending its source emits, unmatched; the branch on the cause is jagt's.
- An end followed at once by a start is declared per runtime and is no ending.
- The watchdog's silence covers only what emits nothing.

## Rejected

- One matcher per cause a feature needs: every cause added later is silence until someone notices.
- Reading the cause off the pane: a vendor's wording, guessed.

## Reopen when

- A runtime emits an ending jagt cannot tell from another.
