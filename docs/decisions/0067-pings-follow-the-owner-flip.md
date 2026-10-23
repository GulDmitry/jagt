# 0067 — Pings follow the owner's flip

[← decisions](README.md) · rule: [`attention`](../../openspec/specs/attention/spec.md)

## Status

Accepted 2026-10-23.

## Context

In `act` with `mine: [plan]` the Master read a plan, wrote `ready` and pressed nothing. The card became the human's
and nobody was pinged: the ping fired from `AgentStatusReports` on a session's report or a read round, and the
Master's verdict and its own question are neither. A step can become the human's without any report.

## Decision

- One job, `YourMoveJob`, pings: every 2 s it projects each task and keeps its last owner-YOU advice.
- It pings when that advice appears or changes; a task met for the first time, a restart included, is recorded silently.
- `AgentStatusReports` pings nothing; `notifyUser` stays, as an agent's own words.
- The map holds only present tasks.

## Rejected

- A ping at each place that writes a status: the next writer without a report is silent again.
- Pinging a card already yours at startup: every restart would repeat what the human has seen.

## Reopen when

- A write that makes a task yours can wait longer than one tick, or a tick pings a move the Master is about to press.
