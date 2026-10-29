# 0108 — A stop ends on the read that disproves it

[← decisions](README.md) · rule: [`infra-wait`](../../openspec/specs/infra-wait/spec.md)

## Status

Accepted 2026-10-29. Extends [0105](0105-infrastructure-failures-are-waited-out.md) and [0100](0100-a-stop-is-scoped-to-its-request.md).

## Context

- An `INFRA` stop set while the code host was down held a resolved deploy conflict two hours after the host came back.
- Only a lift or a new request cleared a stop, and a stopped task is not polled: nothing could see the host was well.

## Decision

- Every `Trigger` names how it ends, `LIFT` or `HOST_READ`, through its one constructor: a new trigger cannot compile without choosing.
- `CHECKS` and `INFRA` end on `HOST_READ`: the condition is outside jagt, a read can disprove it.
- Every other trigger is a judgement and ends on `LIFT`.
- The read is `StopReadJob`, on the auto-review cadence, through `RoundReading`, the read a sweep makes; it runs while the stop stands.
- A read that lists the checks and is not red voids the stop through `TaskStops`; red, unknown or unreadable keeps it.
- The probe relays, ships and presses nothing: the next tick of whoever waited on the stop acts.

## Rejected

- Letting the sweep read a stopped task: a stop's whole meaning is that nothing unattended acts on it.
- A timer that lifts after a delay: it guesses what a read knows.
- Voiding a `LIFT` stop on green: a loop or a ceiling does not end because the checks pass.

## Reopen when

- A voided stop returns within the hour on most tasks: a green read does not disprove the host failing.
- Auto-review is off on an install where `INFRA` stops stand for hours: the read needs its own switch.
