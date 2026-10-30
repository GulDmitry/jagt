# 0090 — A relay is recorded before it is sent

[← decisions](README.md) · spec: [`cases`](../../openspec/specs/cases/spec.md) · relates:
[0076](0076-the-turn-is-a-fact-on-the-task.md) (the turn is a fact on the task)

## Status

Accepted 2026-10-25.

## Context

- The claim-less relays (`write`, `say`, `relayComposed`) sent first and recorded about 0.8 s later.
- jagt dying in between left the session told and nothing recorded.
- A re-press told the session a second time.

## Decision

- One mechanism for every relay: record the turn with its digest mark, then send.
- A failed send restores the turn and stops the task UNDELIVERED.

## Known limits

- `ship`'s status is still applied by the flow engine after the send.
- Claim-less verbs check `Standing` (status, stop kind, turn) against their read, not a flow-side claim.
- `write` re-sends a hand-off by design.

## Rejected

- Send first and dedupe on the next press by the digest: the window where the session is told and nothing is recorded stays.
- A second, per-relay lock across the send: the send types into tmux for seconds and would block every task's writes.

## Reopen when

- A relay cannot be recorded before it is sent.
