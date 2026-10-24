# 0075 — A request that sets off a case says why

[← decisions](README.md) · follows: [0073](0073-cases-are-the-humans-and-the-stand-comes-first.md)

## Status

Accepted 2026-10-24, by the owner.

## Context

A task's history said who asked for each step, never why, so a step nobody expected could not be read back.

## Decision

- `update_agent_status` and `initialize_task` require `why` ([protocol](../../openspec/specs/protocol/spec.md)): one
  sentence of at most 120 characters (`protocol/Why`), refused at the door as `validation` naming the field and limit.
- The reason travels as the origin does, through `OriginContext`, and lands on the `StatusChange` the request
  writes; jagt's own reports and older records carry none.
- The board shows it in the history step's hover alone.

## Rejected

- Threading it through `NewTask` and `Reported`: every signature on the way, for a fact only the step keeps.
- A line on the card: a card never grows.

## Reopen when

- A human's verb or a job sets off a case that needs a reason too.
