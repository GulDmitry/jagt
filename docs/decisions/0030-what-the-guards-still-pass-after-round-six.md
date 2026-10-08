# 0030 — What the guards still pass after round six

[← decisions](README.md) · rule: [`git`](../../openspec/specs/git/spec.md)

## Status

Accepted 2026-10-08. Amends [0029](0029-what-the-guards-still-pass-after-round-five.md); its other rows stand.

## Context

- A sixth review found `mcp__x__get_and_delete_issue` reaching `ReadGate` through the `get*` read glob, its mid-name
  deny left to the CLI.

## Decision

- 0029's row "a deny glob mid-name" is closed: `ReadScope` carries `ReadOnlyTools.MCP_WRITES`, and `ReadGate` refuses
  a tool matching one where only a glob allowed it. A tool the human names in full still passes.

## Rejected

- Leaving the deny to `--disallowedTools`: the CLI's acceptance of a mid-name glob is unproven.

## Reopen when

- A read the human allowed by glob is refused for a verb its name only holds by chance.
