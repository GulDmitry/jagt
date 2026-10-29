# 0108 — A stop ends on the read that disproves it

[← decisions](README.md) · rule: [`infra-wait`](../../openspec/specs/infra-wait/spec.md)

## Status

Accepted 2026-10-29. Extends [0105](0105-infrastructure-failures-are-waited-out.md) and [0100](0100-a-stop-is-scoped-to-its-request.md).

## Context

- An `INFRA` stop set while the code host was down held a resolved deploy conflict two hours after the host came back.
- Only a lift or a new request cleared a stop, and a stopped task is not polled: nothing could see the host was well.

## Decision

- Every `Trigger` names how it ends, `LIFT`, `HOST_READ` or `SESSION_ALIVE`, through its one constructor: none compiles unchosen.
- `CHECKS` and `INFRA` end on `HOST_READ`: the condition is outside jagt, a read can disprove it.
- `UNDELIVERED` ends on `SESSION_ALIVE`: `SessionAliveJob` looks at the session's window every minute and ends the stop once it runs.
- Every other trigger is a judgement and ends on `LIFT`.
- The read is `StopReadJob`, every 5 minutes, whatever auto-review says; it runs while the stop stands.
- It goes through `RoundReading`, the read a sweep makes.
- A read that lists the checks and is not red voids the stop through `TaskStops`; red, unknown or unreadable keeps it.
- A read the host refuses as "no such request", or answers closed unmerged, is its own kind, `GONE`.
- `GONE` voids the stop only for a request it was counted on; `NO_MR`, no request linked, voids it too.
- The refusal kinds that void a stop are one set beside `endOnRead`.
- The probe records the checks and approval it listed through `RoundRecord`, so the card shows what ended the stop.
- The probe relays, ships and presses nothing: the next tick of whoever waited on the stop acts.

## Rejected

- Letting the sweep read a stopped task: a stop's whole meaning is that nothing unattended acts on it.
- A timer that lifts after a delay: it guesses what a read knows.
- Voiding a `LIFT` stop on green: a loop or a ceiling does not end because the checks pass.

## Reopen when

- A voided stop returns within the hour on most tasks: a green read does not disprove the host failing.
