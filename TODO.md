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
