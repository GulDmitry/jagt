# 0024 — Every headless call is metered where it runs

[← decisions](README.md) · rule: [`seams`](../rules/seams.md#every-assistant-call-is-metered)

## Status

Accepted 2026-10-08.

## Context

- One `MasterAssistant` port served nine unrelated reads; a usecase facade, `MeteredAssistant`, wrapped it so
  every call was booked with `UsageTracker`.
- Splitting the port into four read families (`TrackerAssistant`, `RoutingAssistant`, `CodeHostAssistant`,
  `CommandAssistant`) would have needed four facades, each one a caller could bypass by injecting the port.

## Decision

- The shared adapter runner `adapter/assistant/HeadlessClaude` books every call, the round reviewer's included, to
  the session under its kind as soon as the CLI returns, before the answer is judged.
- Callers inject only the port they read and never book the session; the one that knows the task charges it
  (`UsageTracker.chargeTask`), since a ticket is read before its task exists.

## Rejected

- A facade per port: four places to forget, and nothing stops a caller injecting the bare port.
- Metering in each caller: the spend of a call nobody books is the failure this exists to prevent.

## Reopen when

- A second assistant runtime arrives that does not go through `HeadlessClaude`.
