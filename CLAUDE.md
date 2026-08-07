# jagt — Stateful Multi-Agent Dev Orchestrator

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
- `config.json` — user config, grouped into logical sections: `projects` (path, baseBranch,
  deployBranch, labels), `viewer` (tmuxSession, viewMode shared|tab-per-task, keepViewer), `dashboard`
  (refreshSeconds, reservedRows), `codeReview` (mrTitlePattern, postReviewReplies, reviewReplyAuthors,
  mergeRequestDefaults), `agent` (outputStyle), `worktree` (copyGlobs). Each section is a small value
  record (`ConfigService.ConfigFile.*Config`) with `defaults()` + `withX` withers + `*OrDefault`
  accessors; a whole section may be omitted (ConfigFile's accessors coalesce a null section to its
  defaults, so callers never null-check). Gitignored; created by copying committed `config.json.dist`.
  Never commit user-specific paths. ALL config keys are documented in README's Configuration section —
  keep it in sync.
- Orchestrator root is auto-detected at startup: nearest parent dir containing `mcp_client.js`
  (`OrchestratorPaths`); overridable via `ORCHESTRATOR_ROOT`. No absolute user paths in the repo.
- `initialize_task` copies the base repo's IDE files into the worktree so `ide` opens it ready to run
  and query (`copyIdeProjectFiles`): run configs — both `.run/` (modern) and `.idea/runConfigurations/`
  (legacy; only "Store as project file" ones, workspace-only don't copy) — plus the DB connections
  (`.idea/dataSources.xml`, `.idea/dataSources.local.xml`, `.idea/dataSources/`; passwords stay in the
  OS keychain keyed by the source UUID, so they carry over). All are gitignored, hence absent from a
  fresh branch checkout. It ALSO copies gitignored local files matching
  the per-project `worktree.copyGlobs` (default `["**/.env"]`) from the base repo to the same relative
  worktree path (`copyLocalFiles`, heavy dirs skipped) — run configs reference module `.env`, key
  files, SSL certs (e.g. `app/.env`, `**/*.pem`) which are gitignored and otherwise missing, so the
  app wouldn't start. Patterns are config, NOT hardcoded. Best-effort, gitignored, no-op if absent.
- `state.json` — SSOT for tasks (gitignored, auto-created).
  Status enum: NEW, IN_PROGRESS, REVIEW_PENDING, SHIPPING, CI_POLLING, CI_FAILED, REVIEWED,
  DEPLOY_CONFLICT (deploy hit a merge conflict — human resolves it in the deploy worktree), DEPLOYED, DONE.
- `master_prompt.md` — system prompt for the Master session (router, never writes code).

## Session roles
- Master session: Claude in THIS directory (`claude --append-system-prompt "$(cat master_prompt.md)"`),
  delegates via `initialize_task`.
- Sub-agents: Claude in worktrees `<taskId>-<projectKey>` (sibling of the base repo). Their generated
  `CLAUDE.md` carries full system knowledge (orchestrator root, all projects, active tasks) plus per-task
  rules; instructions arrive via `task_context.md`.

## Engineering constraints (do not regress)
- MASTER SHELL = FULL-SCREEN TUI (Lanterna), ONE integrated screen. `MasterShell` runs a Lanterna
  `Screen`: command-output log on top, the dashboard table beneath it, the `jagt>` input line pinned to
  the bottom row — all in one back-buffer, redrawn from scratch every frame (`render()`), refreshed every
  `dashboard.refreshSeconds` (config.json, default 10). Resize is handled by `doResizeIfNecessary()` + the
  full redraw — DO NOT reintroduce a JLine `Status`/scroll-region pinned bar or any absolute-bottom cursor
  anchoring: that could not survive terminal resize (DECSTBM resets on resize → orphaned ghost dashboard +
  the prompt flying to row 1), which cost many sessions. `dashboard.reservedRows` caps the dashboard height
  so ≥ that many rows stay for output+input (overflow → a "… +N" line). Terminal layout IS testable, never
  "fix it blind": `orchestrator-backend/scripts/dashboard-layout-smoke.sh` drives the jar in tmux and
  asserts the invariants (one dashboard header, input pinned to the bottom row, dashboard above it) across
  startup + resize both ways + task-count changes. Run it after ANY change to `MasterShell` rendering.
  No-TTY (e.g. `gradlew bootRun`) falls back to a plain inline line-REPL.
- CRITICAL git safety: the ONLY write to a shared branch anywhere is `deploy` (task branch ->
  `deployBranch`, via `GitService.mergeIntoAndPush`). `ship` creates/updates a merge REQUEST only —
  never merges. The base branch (`baseBranch`, tasks are cut from it) is READ-ONLY: nothing ever
  pushes/merges to it. `deployTask` REFUSES when `deployBranch` == `baseBranch`. Sub-agents are
  forbidden (prompt rule) from pushing/merging anywhere but their own task branch. A worktree branch
  is cut FROM `origin/<baseBranch>` and inherits it as upstream, so `GitService.detachUpstream` unsets
  it right after creation — a bare `git push` then errors ("no upstream") instead of pushing the task
  branch straight into the release branch.
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
- NO GIT HOOKS, EVER — never propose, add, or rely on any git hook anywhere; enforce invariants in code + prompts.
- NO GUI/keystroke automation, ever: System Events keystrokes race with the human typing (they land in
  whatever is focused). Agent terminals are tmux windows (`TmuxService`); visibility comes from one Warp
  window opened via `open warp://launch/jagt-agents` (launch config generated into
  `~/.warp/launch_configurations/`) whenever `tmux list-clients` shows nobody attached.
- PLUGGABLE BY DESIGN — this is a FIRM architectural invariant, do not erode it. jagt targets Linux +
  macOS with SWAPPABLE terminals, notifiers, editors, and AI-agent runtimes (Claude Code / Codex / Qwen /
  … — any MCP-capable CLI). Everything OS- or agent-specific lives behind a STRATEGY INTERFACE, selected by
  config, so adding a new one is "implement the interface + register a config value" — NEVER a hardcoded
  `if claude`/`if macos` sprinkled through the flow. The agent-agnostic task flow (create worktree →
  provision → launch → talk over MCP) must stay free of any single agent's assumptions. The four seams:
  - `UserNotifier` (`orchestrator.platform`, default macos), `TerminalDriver` (`orchestrator.terminal`,
    default `kitty`; `warp` too), `EditorDriver` (`orchestrator.editor-command`) — in `…platform`.
  - `AgentRuntime` (`…agent`, `orchestrator.agent`, default `claude`) — the pluggable AI-agent CLI:
    `launchCommand` + (worktree provisioning + per-agent MCP config) live here. `mcp_client.js` is a
    STANDARD, agent-agnostic MCP stdio↔HTTP proxy (keep it that way); only the config that declares it
    differs per agent (Claude `.mcp.json`, Codex `config.toml`, …) and belongs in each `AgentRuntime`.
  - The shared system-knowledge file is `AGENTS.md` (the cross-agent convention); Claude reads `CLAUDE.md`,
    so its runtime symlinks `CLAUDE.md` → `AGENTS.md`. A new agent = one `AgentRuntime` impl; a Linux port
    = new `UserNotifier`/`TerminalDriver`/`EditorDriver` impls. Nothing else should need to change.
- `KittyTerminalDriver` drives kitty via its remote-control CLI (`kitty @ --to unix:<per-session
  socket>`): one dedicated instance (`--single-instance --instance-group --listen-on -o
  allow_remote_control=yes`), tabs titled + closable (unlike Warp). Runs OVER tmux (tab execs `tmux
  attach`), so agents persist; `closeViewerWindow` kills the instance by its socket path (macOS keeps
  the app alive after windows close, and `close-os-window`/`--match all` are NOT kitty commands).
  Tab decoration comes from tmux `set-titles` → active window name (taskId).
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
- MCP permission gating: Claude Code's auto-mode classifier silently blocks tool calls unless
  pre-approved. Both the Master (committed root `.claude/settings.json`) and every sub-agent worktree
  (generated `.claude/settings.local.json`) need `enableAllProjectMcpServers: true` +
  `permissions.allow: ["mcp__jagt-orchestrator", "Bash(git:*)"]` — the MCP tools AND the agent's own
  git (commit/push its task branch on `ship`), which nobody in the tmux window is watching to approve.
  Miss the MCP entry → `ship`/`feedback` stall on an invisible prompt; miss the git entry → the agent
  freezes on `git commit`/`git push`. Safety on shared branches is NOT this allow-list — it is the
  detached upstream (`GitService.detachUpstream`) + prompt rules; the worktree is the agent's sandbox.
  Regenerated only by `initialize_task`, so an EXISTING worktree keeps its old file — patch it in place
  or re-create the task to pick up a changed allow-list.

## Master assistant (headless one-shot)
- The backend talks to no external systems, but `do <ticket>` needs the Jira ticket read BEFORE a
  worktree/agent exists. `HeadlessClaudeAssistant` (`MasterAssistant`) spawns a one-shot
  `claude "<prompt>" -p --setting-sources user,project,local --json-schema '<schema>'` (stdin
  `/dev/null` via `ProcessRunner`). It hardcodes NO MCP server or path — `--setting-sources` makes the
  child inherit the human's OWN MCP (portable, OS-independent); `--json-schema` forces deterministic
  JSON. Runs from `java.io.tmpdir` so only user-level MCP loads (no jagt project MCP → fewer tokens).
  Project is resolved by intersecting the ticket's labels with each project's `labels`
  (`MasterShell.projectsMatching`); the title is cached for the commit. Any failure → empty → `do`
  falls back to an explicit project. Headless `-p` does NOT auto-load plugin MCP without
  `--setting-sources` (verified: default `-p` sees zero Jira tools).

## Agent resource hygiene
- Each sub-agent is a Claude Code session in a worktree, so each spawns its OWN language server
  (jdtls ~1-2GB per Java worktree) — they can't be shared (worktrees have different uncommitted code;
  LSP is per-root). Agents KEEP their LSP (code intelligence is worth the RAM), so jagt instead REAPS
  each worktree's language server on `done`/`remove_task` (`reapWorktreeProcesses`: `lsof` for procs
  whose cwd is the worktree, `kill -9`) — an orphaned/hung jdtls survives the agent's exit otherwise.
  `orchestrator.agent-disabled-plugins` writes `enabledPlugins: {"<name>": false}` into the worktree
  settings — default EMPTY (opt-in for RAM-constrained setups; disabling an absent plugin is a no-op).

## Conventions
- NEVER use real project identifiers anywhere in this repo — code, tests, comments, docs, examples,
  fixtures. No real ticket keys/numbers, project names, abbreviations, or issue titles from any actual
  project. Always invent obviously fictional placeholders (e.g. `ABC-42`, "Widget layout is off"); the
  existing tests already use `ABC-N` ids — follow that.
- Markdown and docs: aim for ~120-character lines, hard max 150; don't force awkward wrapping.
- Prompt structure (per Anthropic prompt-engineering guidance): wrap concerns in named XML sections
  (`<role>`, `<rules>`, `<output_format>`, `<examples>`); the Master emits fixed-grammar terse lines,
  NOT JSON (no constrained decoding from a CLI system prompt — JSON is cost without guarantee). Forbid
  preamble explicitly; damp deliberation with "respond directly", never "do not think" (that leaks
  `<thinking>` tags). JSON is only for persisted state (`state.json`).

## Testing etiquette
- Smoke tests MUST leave no trace: pass `--orchestrator.open-warp-window=false` (otherwise every test
  run opens a Warp window that stays behind), use a throwaway tmux session + `ORCHESTRATOR_ROOT`, and
  kill the session / remove worktrees + branches afterwards.
- Unit tests: `cd orchestrator-backend && ./gradlew test`. EVERY fixed bug gets a regression unit test
  (sob-ai:unit-testing rules), verified RED by actually reverting the fix and running the test.

## Code quality — the test is the litmus of the production code
- A test is the embodiment of the main code's cleanliness. If a test needs ~5+ objects set up, or its
  cognitive load / composition is high, the SMELL IS IN THE PRODUCTION CODE (poor decomposition /
  isolation), NOT the test — fix the code so the test goes light (sob-ai:unit-testing §5). Never paper
  over it with fatter test setup or shared fixtures.
- No fat constructors / positional null-soup. Keep params to ~4-6; beyond that GROUP collaborators into a
  cohesive component (composition) or use a builder. Config/value records get a builder or a
  `defaults()` + `withX` withers — never call a 10-arg record constructor with a row of `null`s. (Lombok
  `@Builder`/`@Value` is welcome for non-record boilerplate, added deliberately; it does NOT apply to
  records, so jagt's config records need a hand-rolled builder/defaults.)
- Prefer composition over many injected dependencies; SOLID + clean-code defaults — standard for 30 years,
  apply them, don't reinvent.
- SELF-CONTROL LOOP (mandatory, every code+test change): run the changed tests through the
  sob-ai:unit-testing skill (hand off to an agent). If it reports a test as compositionally heavy / high
  cognitive load / too much setup, that is a signal to REFACTOR THE PRODUCTION CODE until the test is
  light — then re-run. Deliver only when tests are BOTH light and green AND reviewed (next bullet).
- CODE REVIEW IS MANDATORY AFTER EVERY CODE CHANGE, BEFORE COMMITTING: run the `code-review` skill (or the
  `oh-my-claudecode:code-reviewer` agent) on the working diff. Fix every real finding (or explicitly note
  why it's a non-issue), then re-review if the fixes are non-trivial. No commit lands unreviewed — this is
  a hard gate, not a suggestion. (A shell hook can only *remind*; it cannot invoke a skill, so this is
  enforced here as a workflow rule, not in settings.json.)

## Build & run
- The backend process IS the Master control terminal (Lanterna full-screen TUI); run it in a REAL terminal
  so the TUI can take over the screen: `./gradlew build` then `java -jar build/libs/jagt.jar` (bootJar has a
  fixed, version-independent archive name, so the run command never changes across releases).
- `./gradlew bootRun` works but Gradle captures stdout → no TTY (`System.console()` is null) → the TUI
  falls back to a plain inline line-REPL (dashboard printed after each command). Run the jar directly for
  the full-screen TUI. Java 25, port 8290.
- Verify: `curl -s localhost:8290/state`.
- GOTCHA — `NoClassDefFoundError` during a startup FAILURE or on `exit` is NOT a code bug; do NOT "fix" it
  by preloading classes. The missing class VARIES (`ThrowableProxyUtil`, `STEUtil`,
  `SpringBootExceptionHandler`, any lazily-loaded class) precisely because the cause is not any one class:
  `./gradlew build` rewrites the fat jar IN PLACE (same inode — verified), so rebuilding WHILE a JVM runs
  from that jar corrupts its class loading, and the first not-yet-loaded class fails — which then MASKS the
  real error (e.g. "Port 8290 already in use") behind a confusing logback/Spring trace. It is expected and
  harmless: the OLD instance dies, just restart from the freshly built jar. To avoid it entirely, run from a
  copy the build never touches: `cp build/libs/jagt.jar /tmp/jagt.jar && java -jar
  /tmp/jagt.jar`. (Past sessions burned hours chasing this as a logback/preload bug — it is not.)
