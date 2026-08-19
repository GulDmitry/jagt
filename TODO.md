# jagt — TODO

## Finish the rings as a build graph (open)

The kinds and the rings are in place and `RingsTest` keeps the direction, but the compiler is not yet the one
enforcing it. Two placements have to be settled first, and both are judgement calls, not typing:

- `config/` — the use cases read its property records (`OrchestratorPaths`, `OrchestratorProperties`,
  `PromptTemplates`), so it is INNER, not assembly. It also holds `FlowWiring`, which is assembly.
- `startup/` — `StartupCheck` is a contract adapters implement, so that interface is a port; `StartupValidation`
  and the four concrete checks read config and adapters, so they are not.

Then: `:core` (task, flow) `:ports` `:usecase` (capability, job, notify, service) `:surface` `:adapter`, with the
existing project staying the app so `build/libs/jagt-run.jar` does not move.

## Composition validation (open)

The config half is built (`startup/StartupCheck` + `StartupValidation`, one report of everything wrong). The
composition half is not: every flow rule names a registered capability, every status reachable from NEW with a way
out, every (status × capability × outcome) decided, a job's watched statuses exist, a required port capability is
present in the SELECTED adapter, one owner per id.

## Two vendors are still inside the rings (open)

- tmux has NO port: `service/TmuxService` is named directly by everything that opens a session, and
  `startup/ToolchainCheck` refuses to start without the binary. Windows has no tmux, so this is what a port would
  be for — `SessionHost` (create, attach, kill, is-alive, send a line) with tmux as one adapter.
- `orchestrator.claude-command` names one agent in a key a human types. Renaming it breaks existing `config.json`
  files, so it is the owner's call, not a refactor's.

## The outer rings' direction is not asserted (open)

`RingsTest` covers `task/`, `flow/` and `port/`. Two outward imports exist above them and are named in
ARCHITECTURE.md: `surface/board/AgentTerminalController` → `adapter/TtydWebTerminal` (wants a port) and
`service/GitService` + `service/TmuxService` → `adapter/ProcessRunner`.

## `service/` is the unsorted half (open)

40-odd classes: git, state, config, tmux, worktrees — and the renderers both surfaces use (`DashboardRenderer`,
`StateViews`, `StatsReport`, `CycleTimeRenderer`, `UsageStatsRenderer`, `CommandReference`), which are surface work
sitting in the shared pile.
