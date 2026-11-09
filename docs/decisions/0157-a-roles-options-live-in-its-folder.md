# 0157 — A role's options live in its folder

[← decisions](README.md) · rule: [`acceptance`](../../openspec/specs/acceptance/spec.md),
[`startup`](../../openspec/specs/startup/spec.md#requirement-configuration-lives-in-jagtyml) · amends
[0130](0130-every-third-party-is-a-configured-seam.md) for a role's runner

## Status

Accepted 2026-11-09 by the owner's order: no alias, as 0155.

## Context

- What acceptance checks, where and with which tools depends on each person's work: there may be no web app, no
  Playwright, no test tool. No option set is universal.
- `roles/<id>/role.yml` already layers over its `.dist` per key, gitignored; `jagt.yml` held the rest.

## Decision

- **jagt ships the frame**: the trigger, the read-only read, its outcomes, waits, rounds and questions, the runner hook.
- **A role's own options live in its folder**: `model`, `environments` and `flows` in `roles/<id>/role.yml`, described
  in its `role.yml.dist`; `jagt.yml` keeps only `roles.<id>.enabled`.
- The folder is read on every check, so an edit holds with no restart.
- One still written in `jagt.yml` refuses the start, naming its `role.yml` key.
- The flows driver is chosen there too, not by a Spring property; an unknown one, no environment, or a driver
  without path or command is one mark on the board's head, ended by the read that finds it fixed.
- A role's `tools.mcp` adds the read-shaped tools of MCP servers its read loads; a browser server also navigates and
  looks. The read gate holds a navigation's URL argument to the environments as it holds curl; a click or a script stays refused.
- An optional `roles/<id>/setup.md` is the user's own setup brief; nothing in jagt reads it.

## Rejected

- A setup verb in jagt: nothing universal to set up.

## Reopen when

- A redirect or the tab already open in the user's browser shows a read reaching past its environments: the gate sees
  a navigation's URL argument, not where the browser lands.
