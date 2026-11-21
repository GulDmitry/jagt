## Why

Four hand-written servlet filters judged the board's doors beside Spring Security, each normalising `;params` and `%xx` by hand; four Defect-family H fixes followed.

## What Changes

- One Spring Security filter chain per door: refused requests, `/mcp` and `/api/agent/`, the board.
- `StrictHttpFirewall` rejects a respelled path (`;params`, encoded dots) with 400 before any door judges it.
- A peer backed off after failed logins is refused by an authentication provider ahead of the password check.

## Impact

- `openspec/specs/board/spec.md`
