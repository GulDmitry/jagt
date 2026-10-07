# 0021 — jagt runs from a clone of its repository

[← decisions](README.md) · rule: [`components.md`](../rules/components.md)

## Status

Accepted 2026-10-07.

## Context

- `OrchestratorPaths.findRoot` walks up for `jagt.yml.dist` or `mcp_client.js`, and `AbstractAgentRuntime`
  links the bridge from disk, so the jar starts only inside a clone.
- Making it run alone needs both as classpath resources and a decision on what `root` means once nothing
  marks it, since worktrees are cut beside it.
- Only developers use jagt, and a developer clones a repository as a matter of course.

## Decision

- jagt is installed by cloning its repository and running the staged jar there.
- No standalone jar, installer or package until users who are not developers need one.

## Rejected

- A self-contained jar now: a day of work and an open meaning of `root`, for users jagt does not have.

## Reopen when

- Someone who is not a developer is to run jagt.
- jagt grows a layer a clone cannot carry — a Docker Compose stack, a database for its own memory — and its
  packaging is decided with it.
