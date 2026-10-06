# 0006 — The machine owns every move; a model only judges

[← decisions](README.md) · rule: [`task-flow`](../../openspec/specs/task-flow/spec.md)

## Status

Accepted 2026-08-19 (`3195708`), recorded retroactively 2026-10-02.

## Context

- The first Master was a Claude chat driven by `master_prompt.md` (`5ab120b`, 2026-07-25). By 2026-07-29
  routing and the dashboard were already deterministic, and the Master side needed no model (`ece29c4`).
- `ship` was five steps of prose relayed to the agent: the permission classifier stalled `git commit` unwatched,
  the title came back reworded, the request URL was sometimes never reported (`3099b05`, 2026-08-13).
- Legality lived in `Move` and transitions were scattered across four classes (`ARCHITECTURE.md` before
  `3195708`), so a card could advertise a move the gate then refused.

## Decision

- `flow/FlowRules` is the whole life of a task: which status allows which action, and where each outcome leads.
  `FlowEngine` and `FlowReports` are its only two doors, and only they call `withStatus` (`3195708`).
- A capability reports an `Outcome` and names no status; a task reporting its own status is refused unless the
  table allows it.
- The board offers `Move.actions()`, which is `FlowRules.allowed(...)`: an illegal move is never offered.
- Free text maps to one command through the same gate: "a model's guess can never widen what is legal"
  (`e9591ea`). A typed line that parses runs without a model (`635cae8`).
- The Master in `act` is bounded by `FlowRules` like every caller, and its trigger is a status and a stamp
  (`af6eb56`, 2026-09-24).
- Light work runs on a light model: the assistant ships as `haiku` (`9abd9bd`), ~$0.06 a call against ~$0.41 on
  the inherited default; the heavy model reads what came back.
- Written down in `master-brief.md.dist` (`a1c2b6a`).

## Rejected

- A Master chat holding the flow in a prompt: `master_prompt.md` deleted as dead (`a85f454`).
- `ship` as prose an agent follows: three failure modes, no step needing judgement (`3099b05`).
- A resident local model for free text: RAM and swap pressure; headless Haiku instead (`283768a`).
- A local embeddings model for the Master: parsing is grammar, and tool calling needs a capable model (`ece29c4`).
- The table as config: in Java the compiler checks every status and action.

## Reopen when

- A transition is needed that only a model can decide, and no guard over `flow/Facts` can express it.
- A light model's answers come back wrong often enough that the heavy one redoes the work.
