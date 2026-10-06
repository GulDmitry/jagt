# 0007 — Only `adapter/` names a vendor or an OS

[← decisions](README.md) · rule: [`seams.md`](../rules/seams.md) · map: [`ARCHITECTURE.md`](../../ARCHITECTURE.md)

## Status

Accepted 2026-07-25 (`5ab120b`), recorded retroactively 2026-10-02.

## Context

- The first commit already put `UserNotifier`, `TerminalDriver` and `EditorDriver` in `platform/`, "the ONLY
  place OS-/app-specific code is allowed", selected by config (`5ab120b`).
- The agent was not a seam: `TmuxService` built `claude` into the launch command. `AgentRuntime` moved it behind
  a port, selected by `@ConditionalOnProperty` like the platform strategies (`1952bbb`, 2026-08-01).
- Codex, the second runtime, forced `provisionWorktree` into the port: the context file became `AGENTS.md` for
  every agent, Claude getting a `CLAUDE.md` symlink, and nothing outside a runtime names its files
  (`99e7564`, 2026-08-13).
- On 2026-08-19 the rule stopped being greppable and became asserted:
  - `dc2758f`: folders named after the kinds, the OS in `adapter/`.
  - `c8c168b`: `RingsTest`, which found two leaks — `ProcessRunner` in `service/`, a use case importing
    `adapter/agent/AgentWorktree`.
  - `61938cb`, `035883d`: one Gradle module per ring, so an outward import does not compile.
  - `6c8fab5`, `9bcadb3`: tmux behind `SessionHost`, the agent binary as that agent's key.
  - `ad68558`: root `AGENTS.md`, `CLAUDE.md` a symlink.

## Decision

- Every OS- and agent-specific piece is a strategy behind a `core/port` interface, chosen by a config value —
  never `if claude` or `if macos`.
- Only `adapter/` names an OS or a vendor (`VendorNamesTest`, in code); `core/` imports no Spring and no Lombok.
- A new vendor or OS is one implementation plus a config value; a seam selected for the wrong OS is refused at
  startup.
- The shared knowledge file is `AGENTS.md`; nothing is named after one vendor.

## Rejected

- A generated `CLAUDE.md` per worktree (`5ab120b`): another CLI reads `AGENTS.md` (`99e7564`).
- Greppable rules with no test: `RingsTest` found two leaks the first time it ran (`c8c168b`).
- The test alone: until the modules existed "the compiler is not the one enforcing the rule" (`c8c168b`).
  `RingsTest` stays for what one module cannot separate.

## Reopen when

- A port's second implementation needs a change above `adapter/` to plug in.
- A vendor-named key or file has to be read outside the one adapter it belongs to.
