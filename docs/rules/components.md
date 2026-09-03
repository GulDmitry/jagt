# Components, sessions and state

[← AGENTS.md](../../AGENTS.md)

## Components

`orchestrator-backend/` is the Spring Boot app ("The Brain"): state manager, git lock, MCP HTTP server
(`POST /mcp`), watchdog, auto-review scheduler, macOS automation, board on loopback. Reaching it is the
`AgentRuntime` seam's (`adapter/agent/McpEndpoint`).

- **HTTP** first (Claude, Qwen): the CLI is pointed at `orchestrator.mcp-url` and carries
  `X-Working-Directory: <worktree>` itself.
- **stdio** for Codex, which can only *spawn* a server: `AbstractAgentRuntime.linkStdioProxy` links
  `mcp_client.js` (same header, `ECONNREFUSED` retry only).
- `.mcp.json` is Claude Code's project MCP config, **generated per worktree** by `ClaudeAgentRuntime`, never
  symlinked: the header value *is* that path. Codex's is `.jagt/codex/config.toml`.

### Whoever works on jagt reads the same file and reaches the same server

The root is provisioned for all three CLIs as a worktree is: **a rule only one vendor loads is a rule half the
sessions break**.Qwen finds it via `context.fileName` and declares the server with `trust` in `.qwen/settings.json`; Codex needs a
**trusted** project.

- `jagt.yml` is user config, gitignored, copied from `jagt.yml.dist`: ONE file, one
  `orchestrator` root, re-read on every access, Spring binding it once for `orchestrator.*`. Sections are
  omissible value records (`ConfigService.ConfigFile.*Config`). Never commit user-specific paths; every key
  belongs in `docs/configuration.md`.
- Root detection: the nearest parent holding `jagt.yml.dist` **or** `mcp_client.js` (`OrchestratorPaths`);
  `ORCHESTRATOR_ROOT` overrides.
- `initialize_task` copies gitignored IDE and local files best-effort (`copyIdeProjectFiles`,
  `copyLocalFiles`); the per-project `worktree.copyGlobs`, default `["**/.env"]`, are config, not code.

## `state.json`

SSOT for tasks, gitignored, auto-created. Its statuses are [flow.md](flow.md)'s.

- `history`: every status a task moved **to**, with when and **who asked**, oldest first, capped at 50.
- The asker (`task/ActionOrigin`) rides `service/OriginContext` and is set at an **entry point** only —
  `surface/board/OriginFilter`, `NaturalLanguageDispatch`, `AutoReviewScheduler` — never passed down.
- `StateService` writes atomically and keeps `state.json.bak`, recovering from it and moving an unparsable
  file to `state.json.corrupt`; with no usable backup it **throws** — never make that path fail soft.

## Session roles, tasks and scope

- **Master** is the backend process itself: a verb runs in-process, no LLM, no MCP round-trip.
- **Sub-agents** are agent-CLI sessions in worktrees named `<taskId>-<projectKey>`, siblings of the base repo,
  briefed by `AGENTS.md` (`AgentRuntime.SYSTEM_KNOWLEDGE_FILE`) and by `task_context.md`.
- **A task is created with its item's own facts or not at all.** `TaskLauncher` reads the reference on every
  launch; `TicketFacts.usable()` gates on a key, a title **and** a link. A failing answer is asked again
  (`TicketReader`: 5 attempts, 2s apart, under two minutes), a bare key answered for a **different** key is
  refused, and nothing invents a URL. **A line opening on a project key names no item**: the words after it ARE
  the task and `TaskName.from` cuts its branch out of them.
- **Sub-agents can only act on their own task**: `surface/mcp/CallerScope` enforces X-Working-Directory.
  A new MCP tool taking a taskId gets a **row** in `McpToolScopeTest`; four of seven were once unscoped.
  `initialize_task`, `remove_task`, `deploy_task` and `revert_task` are Master-only; every MCP call from a
  registered worktree bumps `lastActiveTimestamp`.
- **A task id is any name git accepts as a branch** (`core/task/TaskName`): a task IS its branch, and every
  directory, tmux session, socket and temp file goes through `TaskName.slug`.
- **The MCP transport must never emit non-JSON-RPC bytes**: malformed JSON → `-32700`, an HTTP error → a
  synthesized JSON-RPC error in `mcp_client.js`, never a Spring error page.
- **`WorktreeOrphanScanner` only ever looks**: one WARN line per unowned worktree (uncommitted work, copied
  secrets), **nothing deleted**. No surface offers it — the board dialog and `GET /orphans` were
  removed and do not come back.

### What is missing is said at startup, not at the click that needed it

- `startup/StartupValidation` asks every `StartupCheck` before the board is announced and refuses the start
  with **all** problems at once (`Misconfigured` via `StartupFailure`), each line naming its key.
- A check lives **next to** the part it answers for, so it exists only when that part was selected; what no
  implementation answers for goes in `startup` — a `type` selecting nothing, `jagt.yml`, jagt's paths, git,
  tmux.
- Two limits: **nothing reaches the network** (presence, never validity) and nothing asks a
  remote about a branch. `orchestrator.startup-checks=false` is for test harnesses only.
- `surface/board/AbortedConnectionFilter` drops the aborted-peer `setSoLinger` `SocketException`. Never
  silence `NioEndpoint` instead.
