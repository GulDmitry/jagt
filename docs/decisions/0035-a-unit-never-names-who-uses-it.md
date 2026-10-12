# 0035 — A unit never names who uses it

[← decisions](README.md) · rule: [`AGENTS.md`](../../AGENTS.md#first-priority), [`style.md`](../rules/style.md#code-quality)

## Status

Accepted 2026-10-12.

## Context

- The owner's first priority: one source of truth, no leaking abstraction, composition pinned by small tests.
- `RingsTest` held imports only; a port or service comment could still name the surface, job or session it serves.
- A review found four: `AgentRuntime`, `Pipeline`, `Rounds`, `StateService`.

## Decision

- A unit states its own contract; who calls it, and why they do, lives with the caller.
- `RingsTest` fails a module naming a type of a module around it, a comment included.
- A caller named as a concept ("the board", "the Master") is the review's: the architect role asks it.

## Rejected

- A word list of concepts in `RingsTest`: the same words are the domain's in `task/` and `flow/`.

## Reopen when

- A module is split or merged: the inner/outer pairs in `RingsTest` follow the modules.
