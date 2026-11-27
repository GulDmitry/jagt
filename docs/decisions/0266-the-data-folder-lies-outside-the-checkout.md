# 0266 — The data folder lies outside the checkout

[← decisions](README.md) · supersedes the default of [0190](0190-the-data-folder-is-data-under-the-root.md)

## Status

Accepted 2026-11-27.

## Context

- 0190 defaulted `orchestrator.data` to `<root>/data`, so `tasks/<task>/worktrees/` lay inside jagt's own checkout.
- The agent CLI reads rule files and hooks from every parent of its working directory:
  a managed session loaded jagt's `AGENTS.md`, git-ignored local rules and `.claude` hooks as the project's own.

## Decision

- `orchestrator.data` defaults to `~/.jagt/data`, resolved from the host's home, never an absolute path in a default.
- An existing `<root>/data` holding `jagt/state.json` stays in use while the setting is unset (`OrchestratorPaths.defaultData`).
- `DataFolderStart` names it once at startup: WARN `data folder kept inside the root`, fields `path`, `setting`, `move`.
  jagt never moves it: tasks record absolute worktree paths.
- `WorkspaceCheck` notes any data folder inside a checkout holding a file of `AgentRuntime.projectAgentFiles()`, naming
  the path and `orchestrator.data`. It warns and never refuses: a refusal would stop every install 0190 created.
- The container keeps `/data`; `ORCHESTRATOR_DATA` and `JAGT__orchestrator__data` still win.
- `DataFolderStart` still moves an older `~/.jagt` holding `state.json` into the data folder.

## Rejected

- Moving `<root>/data` for the owner: in-flight tasks would lose their worktrees.
- Refusing a data folder inside a checkout: an explicit choice stays the owner's.

## Reopen when

- The agent CLI stops loading rule files from parent folders of its working directory.
