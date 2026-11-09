# 0130 — Every third party is a configured seam, and jagt's own reach is the board's

[← decisions](README.md) · rule: [`seams`](../rules/seams.md) · reopens [0003](0003-tracker-and-code-host-are-read-through-the-agents-own-mcp.md)
for the deploy read [0109](0109-a-deploys-outcome-is-the-pipeline-jagt-reads-on-its-commit.md) made jagt's own

## Status

Proposed 2026-11-02 by the owner's order; accepted with the commit landing the code host seam and the board-wide mark.
Amended by [0157](0157-a-roles-options-live-in-its-folder.md): a role's runner is chosen in its folder.

## Context

- 0109 made jagt read the deploy pipeline itself through `glab` or `gh` without reopening 0003, which says the code
  host is no seam of jagt's; `PipelineAddress.ofRemote` guessed the provider: any host without `github` in its name
  was GitLab.
- `glab` signed out of a self-hosted host made two deploys stop on `404 Project Not Found`, each card asking the human,
  while every session went on pushing and opening requests through the MCP.

## Decision

- **Every third-party service or technology is a seam**: a port in `core/port`, one driver per vendor in `adapter/`,
  selected by a `jagt.yml` key with a default. Never guessed from a name, a URL, a host or a file present.
- **The code host is a seam for jagt's own reads**: `codeHost` — `github` (default), `gitlab`, `command` (any provider:
  a configured command prints the pipeline's state) or `none` (no pipeline is read). The CLI or the command holds the
  credential, jagt none, as 0003 holds.
- **A failure of jagt's own reach is jagt's, never a card's**: a driver missing, signed out, refused or unreachable is one
  mark on the board's head naming the fix, ended by the next read that succeeds, which runs while it stands. Only a
  failure of the task's own work stops its card.
- Startup checks the selected driver's presence (`components.md`: presence, never validity).

## Rejected

- Guessing the provider from the remote: a self-hosted host has no name to read it from.
- Stopping each task on a reach jagt lost: N cards ask one question, and no lift can answer it.

## Reopen when

- A driver's presence cannot be checked without the network.
