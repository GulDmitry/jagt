# 0082 — A prompt before the harness is read off the window

[← decisions](README.md) · rule: [`seams.md`](../rules/seams.md#an-ending-says-which-one) ·
spec: [`attention`](../../openspec/specs/attention/spec.md) · amends:
[0052](0052-an-ending-says-which-one.md) (its rejection of reading the pane)

## Status

Accepted 2026-10-24.

## Context

- On a stand run 9 of 24 sessions sat on Claude's trust dialog for ten minutes, then were ended as "no sign of life".
- The dialog opens before Claude loads any hook, so no hook can report it; its window is the only sign.

## Decision

- At `NEW`, a running session's window is read for a prompt jagt never answers; the runtime declares the phrases
  (`trust-dialog` in `hooks/<runtime>.properties`) and names the cause.
- Such a session is stopped as waiting, with that cause, within seconds.
- jagt reads the window only and never answers the prompt.

## Rejected

- Answering the dialog with a key: keystroke automation.
- Waiting for the watchdog's threshold: ten minutes for what the screen shows at once.

## Reopen when

- The CLI reports its trust dialog through a hook.
