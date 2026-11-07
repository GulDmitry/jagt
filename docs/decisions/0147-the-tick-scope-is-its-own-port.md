# 0147 — The tick scope is its own port

[← decisions](README.md) · rule: [`seams`](../rules/seams.md)

## Status

Accepted 2026-11-07.

## Context

- The tick scope was a default method on `SessionHost`; a Mockito mock of the port answers a default with nothing.
- The board suite mocks `SessionHost`, so no scheduled job ran there until its fixture answered the method.

## Decision

- `ReadScope` in `core/port` holds the one method `once(Runnable)`; `TmuxReads` implements it; `TickScheduler` takes it.
- A port method with a body is a trap wherever the port is mocked: behaviour a caller needs belongs on a port of its own.
- The board context needs no stub: `TmuxReads` is a real bean there, the mocked host never reaches tmux.

## Reopens when

- A second reader of a window or process table needs a scope that differs from the tick.
