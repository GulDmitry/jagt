## Why

What acceptance checks, where and with which tools depends on each person's work: there may be no web app, no Playwright
and no test tool. jagt ships the frame; everything specific lives in the user's gitignored role folder.

## What Changes

- `model`, `environments` and `flows` move from `roles.acceptance` in `jagt.yml` to `roles/acceptance/role.yml`, layered over
  `role.yml.dist` and read on every check; `jagt.yml` keeps only `enabled`.
- Any of the three still written in `jagt.yml` refuses the start, naming the `role.yml` key; no alias.
- A role's `tools.mcp` names MCP servers or tools its read loads beside `assistant.allowedTools`, read-shaped as there; a browser
  server also navigates, its URL held to a listed environment as curl is, and takes snapshots and screenshots, writing no file.
- An enabled acceptance whose folder names no environment, an unknown flows driver, a driver without path or command, an
  unreadable `flows.timeout` or a `tools.mcp` entry naming no server is one mark on the board's head, ended by the read that finds it fixed; the unknown driver no longer refuses the start.
- The `.dist` files are a minimal example with no Playwright and no web app; an optional `setup.md` is the user's own.

## Impact

- Specs: `acceptance`, `acceptance-probes`, `startup`.
- `RoleOptions`, `RoleSettings`, `RoleDefinitions`, `ConfigService.RoleConfig`, `ConfigCheck`, `RoleConfigOrigins`, the checker
  jobs, `adapter/flows` (`FlowsRunner`, `PlaywrightRunner`, `RoleFolderReach`), `HeadlessClaudeCheckerReader`.
- `AGENTS.md`, decision 0157, `jagt.yml.dist`, `roles/acceptance/*.dist`.
