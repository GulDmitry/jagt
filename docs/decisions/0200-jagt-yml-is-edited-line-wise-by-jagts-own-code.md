# 0200 — jagt.yml is edited line-wise by jagt's own code

[← decisions](README.md) · on [0130](0130-every-third-party-is-a-configured-seam.md)

## Status

Accepted 2026-11-19, owner's choice (JAGT-47): the file keeps its comments and key order; `ProjectBlocks` already edits it as lines.

## Context

- The board changes jagt's basic settings in `jagt.yml`, a file the human also reads and edits: its comments are the documentation.
- AGENTS.md takes the industry-standard library before own code; none keeps the layout. Measured 2026-11-19 on `jagt.desktop.yml.dist`:

| library | result |
|---------|--------|
| SnakeYAML Engine 3.2, comments on | keeps all 280 comments, but adds trailing spaces on blank lines, collapses aligned comments, re-indents comment blocks (344 to 347 lines) |
| Jackson YAML 3.3.0 | drops every comment |
| eo-yaml 8.0.6 (2024-08) | no in-place edit |

- SnakeYAML Engine is YAML 1.2: `off` and `on` stop being booleans, so `ConfigSource`'s reading would change with it.

## Decision

- jagt's own code (`SettingLines`, `ProjectBlocks`) changes the file as lines: only the key's own line moves.
- Comments, blank lines, alignment and key order stay.
- A key not written is added at the end of its section; an inline mapping on the way is rewritten as a block.
- `ConfigWriter` is the one writer: it reads the file back and puts the last good one back, reporting a `ConfigWriteFailed`.
- The settings page's `GET` and `PUT /api/settings` raise `publicSurface` 81 to 83 (owner 2026-11-19: the raise is small).

## Rejected

- SnakeYAML Engine round trip: the layout changes on every write, and the YAML version with it.
- Jackson YAML: drops the comments.

## Reopen when

- A maintained library round-trips `jagt.desktop.yml.dist` byte-identically.
