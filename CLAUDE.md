# jawo — Stateful Multi-Agent Dev Orchestrator

Local, macOS-only orchestration of Claude Code CLI sessions across isolated Git worktrees.
NOT cross-platform by design: macOS + Warp + IntelliJ IDEA (`idea` CLI) + Java 25 / Spring Boot 4.x only.
Jackson is v3 (`tools.jackson.*` packages, unchecked exceptions); annotations stay `com.fasterxml.jackson.annotation`.
Build tool: Gradle, Groovy DSL only (wrapper committed). Never introduce Maven or Kotlin (incl. `.kts`).

## Components
- `orchestrator-backend/` — Spring Boot app ("The Brain"): state manager, Git lock, MCP HTTP server
  (`POST /mcp`), Watchdog, macOS automation (osascript).
  Runs in a visible foreground Warp tab: `cd orchestrator-backend && ./gradlew bootRun`.
  The backend talks to NO external systems (no GitLab/Jira API clients, no tokens): agents use their
  own Claude MCP integrations for that; `CI_POLLING` is handled by the Master via its GitLab MCP.
- `mcp_client.js` — Node.js stdio→HTTP MCP proxy. Injects `process.cwd()` as `X-Working-Directory` header
  so the backend knows which agent is calling. Symlinked into every worktree.
- `.mcp.json` — Claude Code project MCP config pointing at `mcp_client.js` (spec called it `.claude.json`;
  `.mcp.json` is what Claude Code actually reads). Symlinked into every worktree.
- `config.json` — user config: projects (path, baseBranch, deployBranch, labels), `tmuxSession`,
  `viewMode` (shared | window-per-task), `mergeRequestDefaults`. Gitignored; created by copying
  committed `config.json.dist`. Never commit user-specific paths. ALL config keys are documented
  in README's Configuration section — keep it in sync.
- Orchestrator root is auto-detected at startup: nearest parent dir containing `mcp_client.js`
  (`OrchestratorPaths`); overridable via `ORCHESTRATOR_ROOT`. No absolute user paths in the repo.
- `state.json` — SSOT for tasks (gitignored, auto-created).
  Status enum: NEW, IN_PROGRESS, REVIEW_PENDING, CI_POLLING, CI_FAILED, DONE.
- `master_prompt.md` — system prompt for the Master session (router, never writes code).

## Session roles
- Master session: Claude in THIS directory (`claude --append-system-prompt "$(cat master_prompt.md)"`),
  delegates via `initialize_task`.
- Sub-agents: Claude in worktrees `<taskId>-<projectKey>` (sibling of the base repo). Their generated
  `CLAUDE.md` carries full system knowledge (orchestrator root, all projects, active tasks) plus per-task
  rules; instructions arrive via `task_context.md`.

## Engineering constraints (do not regress)
- All git ops in `GitService` under a per-repository `ReentrantLock` (index.lock races are per-repo;
  a slow fetch in one project must not block another).
- Sub-agents can only act on their own task (X-Working-Directory scoping is ENFORCED in
  `resolveTaskId`); `initialize_task`/`remove_task` are Master-only. Task ids are validated
  (`[A-Za-z0-9][A-Za-z0-9_-]*`) — they become branch/dir/tmux names.
- The MCP transport must never emit non-JSON-RPC bytes: malformed JSON → `-32700` from the controller,
  HTTP errors → synthesized JSON-RPC error in `mcp_client.js` (never forward Spring error pages).
  The proxy retries ONLY `ECONNREFUSED` (request never sent) — other failures may have executed a
  non-idempotent tool.
- `state.json` writes are atomic (temp file + `Files.move` ATOMIC_MOVE) in `StateService`.
- Every MCP tool call from a registered worktree bumps `lastActiveTimestamp` (Watchdog keep-alive).
- NO GUI/keystroke automation, ever: System Events keystrokes race with the human typing (they land in
  whatever is focused). Agent terminals are tmux windows (`TmuxService`); visibility comes from one Warp
  window opened via `open warp://launch/jawo-agents` (launch config generated into
  `~/.warp/launch_configurations/`) whenever `tmux list-clients` shows nobody attached.
- OS/app-specific code lives behind strategies in `dev.jawo.orchestrator.platform`: `UserNotifier`
  (selected by `orchestrator.platform`, default macos), `TerminalDriver` (`orchestrator.terminal`,
  default warp), `EditorDriver` (`orchestrator.editor-command` list, default `open -a "IntelliJ IDEA"`).
  A Linux port = new impls of these three interfaces + config, no core changes.
- Agent liveness in a tmux window is detected via child processes of `#{pane_pid}` —
  `pane_current_command` always reports the shell (no job control in `sh -c` compound commands).
- One task = one tmux window: `openTaskWindow` kills same-named windows before spawning.
  After the agent exits its window shows the tail for 15s and closes itself — never leave an
  interactive shell in agent windows (it lingers forever and reads as a hung process).
- Closing the Warp window only DETACHES the viewer — agents keep running (tmux feature, by design).
  Killing is explicit: `done`/`remove`/`close_task_tab`.
- Warp facts (verified empirically + docs sweep 2026-07, don't re-litigate): the URI scheme is the
  ENTIRE programmatic surface (no CLI/IPC/AppleScript dictionary/MCP for the UI). Viewer tabs are
  opened via Tab Configs — TOML generated into `~/.warp/tab_configs/<session>.toml` (`[[panes]]`
  needs a mandatory `id`), opened with `warp://tab_config/<name>` (active window; `?new_window=true`
  for a fresh one) — the tab runs `tmux attach` itself, no shell hooks. `new_tab` inherits the
  active tab's group; tab GROUPS have zero API. Tabs are NOT closable programmatically (absent from
  the AX tree, no URI, Warp keeps them after process death) — whole windows ARE closable via
  addressed AXPress. Hence viewMode `shared` is the default; `tab-per-task` leaves dead tabs for
  the human to close.
- Every new install requirement (e.g. tmux via brew) MUST be documented in README's Prerequisites table —
  never install things silently.
- MCP permission gating: Claude Code's auto-mode classifier silently blocks MCP tool calls unless
  pre-approved. Both the Master (committed root `.claude/settings.json`) and every sub-agent worktree
  (generated `.claude/settings.local.json`) need `enableAllProjectMcpServers: true` +
  `permissions.allow: ["mcp__jawo-orchestrator"]`. Miss it → `ship`/`feedback` stall on an invisible
  prompt.

## Conventions
- Markdown and docs: aim for ~120-character lines, hard max 150; don't force awkward wrapping.

## Testing etiquette
- Smoke tests MUST leave no trace: pass `--orchestrator.open-warp-window=false` (otherwise every test
  run opens a Warp window that stays behind), use a throwaway tmux session + `ORCHESTRATOR_ROOT`, and
  kill the session / remove worktrees + branches afterwards.
- Unit tests: `cd orchestrator-backend && ./gradlew test`. EVERY fixed bug gets a regression unit test
  (sob-ai:unit-testing rules), verified RED by actually reverting the fix and running the test.

## Build & run
- Backend: `cd orchestrator-backend && ./gradlew bootRun` (Java 25, port 8080).
- Jar: `./gradlew build` → `build/libs/orchestrator-backend-0.1.0.jar`.
- Verify: `curl -s localhost:8080/state`.
