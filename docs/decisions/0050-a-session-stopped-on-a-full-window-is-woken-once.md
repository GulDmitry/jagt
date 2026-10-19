# 0050 — A session stopped on a full window is woken once

[← decisions](README.md) · spec: [`usage-limits`](../../openspec/specs/usage-limits/spec.md)

## Status

Accepted 2026-10-19.

## Context

- A session that hits a full window stops and stays stopped until a human sees the reset.
- Claude Code's `StopFailure` event fires when a turn ends on an API error; its matcher filters on `error_type`, one value being `rate_limit`.
- 0033 reads the windows and their resets; nothing it reads refuses or slows work.

## Decision

- The stop is a typed report: `StopFailure` with matcher `rate_limit`, declared in `hooks/claude.properties`.
- Opt-in, `usageLimits.wakeOnReset`, default false: a wake is a write into a session, not a read.
- At the stop, one fresh read decides: a window at 100% with a known reset arms one wake at the latest such reset; otherwise none.
- The wake is one line through `SessionHost.nudgeTaskWindow`, sent by a one-minute job.
- It is taken from `session-wakes.json` before it is sent: at most once per stop, across a restart; it holds one entry per task.
- A report of the session working again cancels it; a human's turn reports the same way.
- Still gated by 0033: nothing is refused or slowed, and the wake only says a line a human could.

## Rejected

- Reading the stop off the pane text: a vendor's wording, guessed.
- Polling every stopped session at each usage read: a wake with no stop to anchor it fires twice.
- Sending before removing the wake: a crash between the two says the line twice.

## Reopen when

- A wake is sent into a human's unsent draft.
- The CLI's event or its `rate_limit` value changes.
