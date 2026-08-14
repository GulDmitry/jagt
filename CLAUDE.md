# jagt — Stateful Multi-Agent Dev Orchestrator

Local orchestration of AI coding-agent CLI sessions across isolated Git worktrees. macOS-first (kitty +
IntelliJ IDEA via the `idea` CLI), Java 25 / Spring Boot 4.x — but every OS- and agent-specific piece sits
behind a strategy interface (see PLUGGABLE BY DESIGN), so a Linux port is new driver impls, not a fork.
Jackson is v3 (`tools.jackson.*` packages, unchecked exceptions); annotations stay `com.fasterxml.jackson.annotation`.
Build tool: Gradle, Groovy DSL only (wrapper committed). Never introduce Maven or Kotlin (incl. `.kts`).

## Components
- `orchestrator-backend/` — Spring Boot app ("The Brain") AND the Master console itself: state manager,
  Git lock, MCP HTTP server (`POST /mcp`), Watchdog, auto-review scheduler, macOS automation (osascript).
  Run the jar in a real terminal (see Build & run) — the process IS the Master TUI.
  Outside writes are the sub-agent's job via its own MCP (push, merge request, review replies) — the ONE
  exception the backend may ever do itself is opening a task's review request over `CodeHost`, and today
  nothing calls it. Outside READS have two paths — a one-shot headless agent
  that inherits the human's own MCP (see Master assistant), and, when configured, the read-only `CodeHost`
  REST seam (see PLUGGABLE BY DESIGN). The REST path is opt-in and needs a token in the environment
  (`orchestrator.code-host.*`); with none configured the backend holds no credential at all.
- HOW AN AGENT REACHES THE MCP SERVER IS PART OF THE `AgentRuntime` SEAM, and there are exactly two paths
  (`agent/McpEndpoint` documents both): HTTP — the CLI is pointed at `orchestrator.mcp-url` and carries
  `X-Working-Directory: <worktree>` itself, nothing running in between; or stdio — the CLI can only SPAWN a
  server, so the runtime calls `AbstractAgentRuntime.linkStdioProxy` and gets `mcp_client.js`, the standard
  Node bridge that POSTs the same header. Prefer HTTP: verified against a real session, and it is what took
  Node out of jagt's requirements. `mcp_client.js` exists only for the stdio path (Codex today, whose config
  has no verified remote-server form) — do NOT link it for everybody again.
- `.mcp.json` — Claude Code's project MCP config, GENERATED per worktree by `ClaudeAgentRuntime` (not
  symlinked: the header value IS that worktree's path). The committed ROOT `.mcp.json` is the same server for a
  dev session working ON jagt, with no header — that session is not a task, so the backend treats it as Master.
  Other runtimes write their own equivalent (Codex: `.codex/config.toml`); it is not a universal file.
- `config.json` — user config, grouped into logical sections: `projects` (path, baseBranch,
  deployBranch, labels), `viewer` (tmuxSession, viewMode shared|tab-per-task, keepViewer), `dashboard`
  (refreshSeconds, reservedRows), `codeReview` (mrTitlePattern, postReviewReplies, reviewReplyAuthors,
  mergeRequestDefaults), `agent` (outputStyle), `worktree` (copyGlobs). Each section is a small value
  record (`ConfigService.ConfigFile.*Config`) with `defaults()` + `withX` withers + `*OrDefault`
  accessors; a whole section may be omitted (ConfigFile's accessors coalesce a null section to its
  defaults, so callers never null-check). Gitignored; created by copying committed `config.json.dist`.
  Never commit user-specific paths. ALL config keys are documented in README's Configuration section —
  keep it in sync.
- Orchestrator root is auto-detected at startup: nearest parent dir containing `config.json.dist` OR
  `mcp_client.js` (`OrchestratorPaths`); overridable via `ORCHESTRATOR_ROOT`. Two markers on purpose — the
  bridge is only still here for stdio-only agents, so root detection must not depend on it. No absolute user
  paths in the repo.
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
- `state.json` — SSOT for tasks (gitignored, auto-created). Each task also keeps `history` — every status it
  moved TO, with when, oldest first, capped at 50 (the file is rewritten on every MCP call). A KEEP-ALIVE adds
  nothing (same status = no entry, else four real transitions drown in hundreds of identical rows), and a task
  starts its history at the status it was created with. Read "since when in this status" from
  `TaskState.statusSince()`, NEVER from `lastActiveTimestamp` — a keep-alive bumps that one, so an hour-old
  status would look fresh.
  Status enum: NEW, IN_PROGRESS, REVIEW_PENDING, SHIPPING, CI_POLLING, CI_FAILED,
  REVIEWED (nothing unresolved + CI green), APPROVED (a human actually approved the review request),
  DEPLOY_CONFLICT (deploy hit a merge conflict — human resolves it in the deploy worktree), DEPLOYED,
  REVERTED (its deploy was taken back out; the branch and commits survive, so the next move is a fix), DONE.

## Session roles
- Master = the backend process itself. `MasterShell` parses a fixed grammar and calls `OrchestratorTools`
  in-process: no LLM, no MCP round-trip, no tokens, no drift. There is NO Master Claude session — the
  deterministic REPL/TUI replaced it, and `master_prompt.md` went with it (see git history). The only LLM
  call on the master side is the headless one-shot assistant below.
- Sub-agents: Claude in worktrees `<taskId>-<projectKey>` (sibling of the base repo). Their generated
  `CLAUDE.md` carries full system knowledge (orchestrator root, all projects, active tasks) plus per-task
  rules; instructions arrive via `task_context.md`.

## Control surfaces (web board + console)
- TWO front-ends, ONE core, and the seam is `OperatorUi` (`…ui`, selected by `orchestrator.ui`: web | tui |
  both — default WEB). `OperatorUiRunner` is the only `ApplicationRunner`; a blocking surface (the TUI, which
  owns the terminal) starts last so the board is already serving. Adding a surface must not add a second
  answer to any question the others already answer:
  - "what is this task and what can I do with it" is `model/Move` + `model/TaskView`, built by
    `service/TaskViews`. The TUI, `/status` and `/api/tasks` all render THAT. `Move.shippable` is also what
    `ShipService.requireShippable` calls — the dashboard used to advise independently of the gate, which is
    exactly how they drifted apart.
  - PARITY IS AN INVARIANT, not an aspiration: a capability that exists in ONE surface only is a bug. Per-task
    verbs come from `Move.actions()`, so a new action appears on both at once; everything else needs an explicit
    counterpart, and the ones that were console-only were exactly the ones nobody noticed missing (`resume`,
    `prune`, `stats`, `help`, `orphans` — all added 2026-08-13). Shared text lives in `service/CommandReference`
    (the grammar) and `StateViews` (dashboard + stats), so neither surface renders its own version. The reports
    open in a `<dialog>` over the board, never a new page. ONE deliberate exception to parity: `quit` is
    console-only — stopping the backend belongs to whoever owns the process (Ctrl-C / kill), not to a browser
    button, and nothing is lost by that since agents live in tmux. A shutdown endpoint was built and removed;
    do not add one back.
  - "how is an action executed" is `service/CommandService` (validates against `Move` first, so a stale board
    tab is refused with a sentence, not with a git error three layers down), and "how is a task started" is
    `service/TaskLauncher`. The console parses a command line, the controller parses JSON; neither owns rules.
- `OrchestratorTools` is the MCP-FACING FACADE ONLY (~480 lines, 7 collaborators) — the work lives in
  `service/AgentSessions` (tmux window, focus, kill, relay to `task_context.md`), `service/TaskProvisioning`
  (worktree creation, alias, sub-agent context), `service/ShipService` and `service/WorktreeFiles`. Do NOT put
  a new concern here just because the dependency it needs is already injected — that is exactly how it grew to
  871 lines and eleven collaborators. Note the lesson (TODO.md keeps the long version): a delegating facade
  KEEPS every collaborator it does not shed, so only a group of methods that monopolises dependencies is worth
  extracting; splitting off `deploy`/`prune` was tried and reverted because it ADDED one.
- TWO-TIER DISPATCH: tier 1 is the grammar (typed command / board button) and it stays LLM-free. Tier 2 is
  `service/NaturalLanguageDispatch` — free text (an unknown console line, or the board's ⌘K palette →
  `POST /api/interpret`) goes to a model that only PROPOSES one grammar command; the dispatcher validates the
  task exists and the verb is real, then executes through `CommandService`, so tier 2 can never do more than
  a button. The call is deliberately stripped (`--strict-mcp-config --mcp-config '{"mcpServers":{}}'`, no
  `--setting-sources`): text→command needs no tools, and a loaded MCP server would be paid for in context.
  It answers with the interpretation FIRST ("understood as `ship a1` — …"), and a single unknown word is a
  typo, not a request — it never reaches the model.
- NO LIMIT ON CONCURRENT TASKS, and this is a DECISION, not an omission. A cap (`agent.maxConcurrentTasks`
  + `TaskAdmission`) was built and then REMOVED on the owner's instruction: jagt runs on other people's
  machines, one of which has 100 GB of RAM, so a number picked here is wrong for almost everyone and refusing
  a `do` on that basis is jagt deciding something it cannot know. Whoever wants a bound has the machine's own
  tools for it. Do not reintroduce a cap, a queue, or a "slots" indicator.
- `Phase`/`Owner` are a PROJECTION for humans, never persisted and never a second state machine: `TaskStatus`
  stays the SSOT. Eleven statuses collapse into six phases because four of them read as the one word "review".
- Liveness is deliberately NOT an input to the projection (a tmux probe per task per render); a task stuck at
  SHIPPING is therefore offered SHIP and the gate refuses at execution time if its agent is alive.
- The board is vanilla HTML/CSS/JS under `src/main/resources/static` — NO build step, NO CDN, no external
  asset of any kind (it must work with the machine offline and stay inside the one jar).
- NEITHER surface polls for state: `StateService.onChange` is the one event both use — `TaskEventStream`
  forwards it as SSE, and `MasterShell` sets a dirty FLAG its render loop consumes (Lanterna's screen belongs
  to the UI thread; the listener runs on whichever thread served the agent's MCP call — never paint from
  there). The SSE event carries no payload on purpose: a payload would be a second serialization that could
  disagree with `/api/tasks`. The periodic tick survives in both only for the relative "ACTIVE" clock.
- Drafted review replies are a FILE, not state: `TaskViews` stats `review_replies.md` in the worktree and puts
  a boolean on the projection (presence, not a count — the agent's brief prescribes no per-comment marker, so
  a number would be a guess). Both surfaces announce it, because a human who does not know the convention
  ships a round and posts replies they never read.
- `dashboard-layout-smoke.sh` drives the CONSOLE, so it must pass `--orchestrator.ui=tui` now that the board
  is the default. Run it after ANY change to `MasterShell` rendering. `tui-push-repaint-smoke.sh` is its
  sibling for the event-driven repaint: refresh 60s + a status pushed through `POST /mcp`, so only the listener
  can explain the redraw. Writing `state.json` directly fires NO listener — a test that mutates the file is
  testing the timer.

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
- `ship` is DETERMINISTIC when a `CodeHost` owns the repository: `ShipService` commits the worktree, pushes the
  task branch and opens/updates the review request in-process (`GitService.commitAll`/`pushBranch` +
  `CodeHost.createOrUpdateMergeRequest`), then sets CI_POLLING with the link. No model on that path, so
  SHIPPING is no longer a state a task can hang in. Two things stay deliberate: a REVIEW-ROUND commit message
  is MECHANICAL (`<task> address review comments`) because the backend cannot describe what the agent fixed,
  and posting the drafted `review_replies.md` is still relayed to the agent — a reply needs the thread it
  answers, which `ReviewFacts` does not carry — but as a FOLLOW-UP, never on the critical path. With no host
  configured the old prose relay is kept verbatim: an unconfigured setup must behave as it always did.
- CRITICAL git safety: the ONLY writes to a shared branch anywhere are `deploy` (task branch ->
  `deployBranch`, via `GitService.mergeIntoAndPush`) and its undo `revert` (`revertMergeAndPush`: reverts the
  merge commit deploy recorded, ADDS a commit, never rewrites history, never force-pushes). Both are
  Master-only and both go through `deployTarget`, so they share one deployBranch guard. `revert` refuses
  rather than guess in every ambiguous case: no recorded merge commit (a deploy from before `deployCommit`
  existed — the human gets the by-hand `git revert -m 1` recipe, jagt will NOT search the log), the commit is
  not on the branch, it was already reverted, or the revert conflicts (aborted + cleaned up; unlike a deploy
  conflict there is no half-state worth keeping). `ship` creates/updates a merge REQUEST only —
  never merges. The base branch (`baseBranch`, tasks are cut from it) is READ-ONLY: nothing ever
  pushes/merges to it — and that holds for a PER-TASK base too (`do <ticket> from <branch>`, persisted as
  `TaskState.baseBranch`): it moves what the worktree is cut from and what the merge request TARGETS, never
  what anything merges into. `deploy` stays on `deployBranch` whatever a task's base is; read the effective
  base through `TaskState.baseBranchOr(project.baseBranch())` so the worktree, the MR target and `ide … diff`
  cannot drift apart. `deployTask` REFUSES when `deployBranch` == `baseBranch`. Sub-agents are
  forbidden (prompt rule) from pushing/merging anywhere but their own task branch. A worktree branch
  is cut FROM `origin/<baseBranch>` and inherits it as upstream, so `GitService.detachUpstream` unsets
  it right after creation — a bare `git push` then errors ("no upstream") instead of pushing the task
  branch straight into the release branch.
  `GitService.pushBranch` pushes ONE task branch with an explicit both-sided refspec, never `--force`, never
  `-u` (an upstream is the trap `detachUpstream` removes).
  `prune` deletes LOCAL branches only, never a remote one (that would be an outward write), only branches
  already merged into `deployBranch`, never an ACTIVE task's branch (merged ≠ finished — a task lives until
  `done`), and never without the explicit `prune all`; a bare `prune` is a dry run.
- Watchdog scope is deliberate (`WatchdogService.watches`): it alerts only for statuses where the AGENT is
  expected to be working — NEW, IN_PROGRESS, SHIPPING. Every other status idles by design (CI_POLLING waits
  on the code host, REVIEW_PENDING/REVIEWED/APPROVED/DEPLOY_CONFLICT on the human), and watching those turns
  the alert into noise.
- ONE review sweep per task at a time, whatever triggered it: the guard lives in `ReviewSweepService` because
  the manual `review`, the auto-poll and any future UI button all pass through it (two sweeps = the headless
  read paid twice + two briefs relayed for one review round). `AutoReviewScheduler` keeps its own separate
  guard, which solves a different problem: stopping 60s ticks from QUEUING behind a sweep that runs minutes.
- All git ops in `GitService` under a per-repository `ReentrantLock` (index.lock races are per-repo;
  a slow fetch in one project must not block another).
- Sub-agents can only act on their own task (X-Working-Directory scoping is ENFORCED in
  `resolveTaskId`); `initialize_task`/`remove_task` are Master-only. Task ids are validated
  (`[A-Za-z0-9][A-Za-z0-9_-]*`) — they become branch/dir/tmux names.
- The MCP transport must never emit non-JSON-RPC bytes: malformed JSON → `-32700` from the controller,
  HTTP errors → synthesized JSON-RPC error in `mcp_client.js` (never forward Spring error pages).
  The proxy retries ONLY `ECONNREFUSED` (request never sent) — other failures may have executed a
  non-idempotent tool.
- `state.json` writes are atomic (temp file + `Files.move` ATOMIC_MOVE) in `StateService`. Atomicity covers a
  TORN file, not a BAD one, so every write also copies the previous version to `state.json.bak`, and a read
  that cannot parse the primary recovers from that backup (moving the bad file to `state.json.corrupt`). With
  no usable backup it THROWS: starting with an empty task list over an existing state file would destroy the
  human's data on the next write. Never make that path "fail soft".
- `WorktreeOrphanScanner` only ever LOOKS: worktree directories no task owns can hold uncommitted work AND
  copies of secrets (`worktree.copyGlobs`), so it reports them (startup ping + `GET /orphans`) and deletes
  nothing. Its startup listener catches everything — an `ApplicationReadyEvent` listener that throws fails the
  whole boot, and a diagnostic must never be able to stop the backend from starting.
- Every MCP tool call from a registered worktree bumps `lastActiveTimestamp` (Watchdog keep-alive).
- Tomcat's "Error setting socket options" (`SocketException` at `setSoLinger`) is a connection the peer aborted
  between `accept()` and configuring it — a browser pre-connect, the losing half of a Node client's IPv6/IPv4
  race to `localhost`, a `curl` probe. `SO_LINGER` is simply the first unguarded setsockopt, and Tomcat gives no
  knob (`AbstractProtocol` sets `connectionLinger` in its constructor). `web/AbortedConnectionFilter` drops that
  one event and nothing else — do NOT "fix" it by silencing `NioEndpoint`, which also hides real socket errors.
- CODE REVIEW IS NEVER FULLY AUTOMATED. The auto-review poll (`AutoReviewScheduler` → `ReviewSweepService`)
  only READS and DRAFTS: an approval may advance status, but comments are merely RELAYED to the agent, which
  fixes LOCALLY and writes its intended answers to `review_replies.md`. Nothing is pushed or posted without
  an explicit human `ship`; the loop never ships, deploys, pushes or posts on its own. Every round hands the
  human two artifacts to inspect via `ide <alias>` — the local diff and the drafted replies. Do not erode
  this: the human-in-the-loop gate lives in the OUTCOME, not in who triggered the sweep.
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
  provision → launch → talk over MCP) must stay free of any single agent's assumptions. The five seams:
  - `UserNotifier` (`orchestrator.platform`, default macos), `TerminalDriver` (`orchestrator.terminal`,
    default `kitty`; `warp` too), `EditorDriver` (`orchestrator.editor-command`) — in `…platform`.
  - `AgentRuntime` (`…agent`, `orchestrator.agent`, `claude` default, `codex` the second impl) — the
    pluggable AI-agent CLI: `launchCommand` AND worktree provisioning (`provisionWorktree`, a template in
    `AbstractAgentRuntime` + one per-agent hook) live here. `mcp_client.js` is a STANDARD, agent-agnostic MCP
    stdio↔HTTP proxy (keep it that way) and is linked by the template; only the config that declares it
    differs per agent (Claude `.mcp.json` + `.claude/settings.local.json`, Codex `.codex/config.toml` with
    `CODEX_HOME` pointed at the worktree) and belongs in each `AgentRuntime`. Nothing outside the runtime may
    name an agent's files — `OrchestratorTools` only calls `provisionWorktree` and `displayName`.
  - `CodeHost` (`…codehost`, `orchestrator.code-host.type`, default none) — REST reads of a review request, so
    the sweep costs no model call, plus EXACTLY ONE write: `createOrUpdateMergeRequest` (opening the artifact a
    human then reviews). Never a push, a merge, a comment or an approval — those belong to the human's gates or
    to the agent's own MCP; a `CodeHost` that merges is a bug. The write is idempotent per (source, target) and
    NEVER retitles an open request (`ship` reruns every review round, and the human may have edited the title).
    `ReviewReader` deliberately does NOT fall back to the paid headless read when a configured host fails: that
    would spend money invisibly and hide the misconfiguration. A partial REST read must fail whole — "no
    unresolved comments + green pipeline" ADVANCES a task. Nothing calls the write yet: `ship` still relays to
    the agent until roadmap step 3 moves it into the backend.
  - The shared system-knowledge file is `AGENTS.md` (the cross-agent convention, `AgentRuntime
    .SYSTEM_KNOWLEDGE_FILE`); Claude reads `CLAUDE.md`, so its runtime symlinks `CLAUDE.md` → `AGENTS.md` —
    one file, never two copies to drift. A new agent = one `AgentRuntime` impl; a Linux port = new
    `UserNotifier`/`TerminalDriver`/`EditorDriver` impls. Nothing else should need to change.
- kitty is ONE driver, not one per OS: `AbstractKittyTerminalDriver` (in `…platform`) holds everything —
  remote control, the per-session socket, tabs, reveal, close — and each platform subclass supplies exactly two
  things, `bringToFront()` and `platformOptions()`. macOS needs AppleScript to raise the app (Cocoa) and the
  Cyrillic `cmd+` keymap workaround; Linux needs NEITHER (the WM owns stacking, and kitty's own `ascii`
  shortcut fallback handles a non-Latin layout), so `LinuxKittyTerminalDriver` overrides both with nothing and
  says why. Selection is `orchestrator.platform` × `orchestrator.terminal` via `@ConditionalOnExpression`, and
  `LinuxProfileContextTest` boots the linux profile so a condition typo fails in CI, not on someone's desktop.
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
  pre-approved. The Master needs no permissions at all (it is Java; the committed root
  `.claude/settings.json` exists for a DEV Claude session working ON jagt, which does call the jagt MCP).
  Every sub-agent worktree (generated `.claude/settings.local.json`) needs `enableAllProjectMcpServers: true` +
  `permissions.allow: ["mcp__jagt-orchestrator", "Bash(git:*)"]` — the MCP tools AND the agent's own
  git (commit/push its task branch on `ship`), which nobody in the tmux window is watching to approve.
  Miss the MCP entry → `ship`/`feedback` stall on an invisible prompt; miss the git entry → the agent
  freezes on `git commit`/`git push`. Safety on shared branches is NOT this allow-list — it is the
  detached upstream (`GitService.detachUpstream`) + prompt rules; the worktree is the agent's sandbox.
  Regenerated only by `initialize_task`, so an EXISTING worktree keeps its old file — patch it in place
  or re-create the task to pick up a changed allow-list.

## Master assistant (headless one-shot)
- The backend has no tracker client, but `do <ticket>` needs the ticket read BEFORE a worktree/agent exists.
  The review sweep also goes through here UNLESS a `CodeHost` is configured — with one, `ReviewReader` takes
  the free REST path and this assistant is never spawned for that poll (the dominant per-task cost).
  `HeadlessClaudeAssistant` (`MasterAssistant`) spawns a one-shot
  `claude "<prompt>" -p --setting-sources user,project,local --json-schema '<schema>'` (stdin
  `/dev/null` via `ProcessRunner`). It hardcodes NO MCP server or path — `--setting-sources` makes the
  child inherit the human's OWN MCP (portable, OS-independent); `--json-schema` forces deterministic
  JSON. Runs from `java.io.tmpdir` so only user-level MCP loads (no jagt project MCP → fewer tokens).
  Project is resolved by intersecting the ticket's labels with each project's `labels`
  (`MasterShell.projectsMatching`); the title is cached for the commit. Any failure → empty → `do`
  falls back to an explicit project. Headless `-p` does NOT auto-load plugin MCP without
  `--setting-sources` (verified: default `-p` sees zero Jira tools), and narrowing it to `project` is
  equally fatal — the call runs from the temp dir, where project scope alone resolves to ZERO MCP servers
  (verified 2026-08-13). Keep `user` in the list; the ~7k tokens it costs are what buys the tracker tools.
- EVERY assistant call is METERED, because it is the only place jagt spends model money. `--output-format
  json` wraps the schema-validated answer (`structured_output`, or `result` as a string) together with
  `usage` + `total_cost_usd`; `UsageTracker` books it to the task that triggered it (persisted in
  `state.json`, so it survives a restart) and to the session (in memory). A call is billed BEFORE its answer
  is judged — an errored call was paid for too. Surfaces: the `TOKENS` dashboard column, the `stats` command
  and `GET /stats`. Sub-agent spend is NOT visible here (it lives in the agent's own session) — never
  present these numbers as a task's total cost.
  Measured floor per call (2026-08): ~25k input tokens of CLI baseline context, ~$0.41 on the inherited
  default model vs ~$0.06 on haiku — which is why `orchestrator.assistant.model` SHIPS as `haiku` (blank it
  to inherit the human's own model). The lever is FEWER CALLS (deterministic REST reads), not shorter prompts.

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
- Prompt structure (per Anthropic prompt-engineering guidance) — applies to every prompt jagt WRITES: the
  sub-agent context, the ship/review briefs, the headless assistant prompts. Wrap concerns in named XML
  sections (`<role>`, `<rules>`, `<output_format>`, `<examples>`). Forbid preamble explicitly; damp
  deliberation with "respond directly", never "do not think" (that leaks `<thinking>` tags). Never ask a
  CLI system prompt for JSON by wording alone (cost without guarantee) — the ONE place jagt takes JSON
  from a model is the headless assistant, where `--json-schema` actually constrains decoding. Otherwise
  JSON is only for persisted state (`state.json`).

## Testing etiquette
- Smoke tests MUST leave no trace: pass `--orchestrator.open-warp-window=false` (otherwise every test
  run opens a Warp window that stays behind), use a throwaway tmux session + `ORCHESTRATOR_ROOT`, and
  kill the session / remove worktrees + branches afterwards.
- NO ABSOLUTE macOS PATHS IN DEFAULTS: an external binary is configured by BARE NAME and resolved by
  `platform/Executables` (PATH first, then the known install dirs — Homebrew included, because a GUI-launched
  process has neither prefix on PATH). `tmux-command` used to default to `/opt/homebrew/bin/tmux`, which made
  every task on Linux fail at "Failed to start command"; the agent CLI is deliberately NOT resolved (it runs
  inside the agent's tmux window under the human's own PATH, and the string is what they read on screen).
- ONE SET OF STEPS FOR EVERY HOST: `.github/workflows/ci.yml` and `.gitlab-ci.yml` run the same suites by
  calling the same scripts (`scripts/linux-test-deps.sh` = the package list, `scripts/with-linux-desktop.sh` =
  Xvfb + session bus + notification daemon, then the smoke scripts). A step that exists in one pipeline only,
  or a CI-only code path, is a bug: green in CI and green on a laptop must mean the same thing. Neither
  pipeline needs Docker — the container image is for macOS developers and installs from that same deps script.
  `linuxDriverTest` is gated on CAPABILITY (Linux + the binaries + a DISPLAY), never on "which harness am I in".
- LINUX IS TESTABLE FROM A MAC: `scripts/linux-suite.sh` runs `test` + `e2eTest` + `linuxDriverTest` inside a
  container (`docker/linux-suite.Dockerfile`). `linuxDriverTest` (source set `src/linuxTest/java`, NOT in
  `check`, guarded by `JAGT_IN_CONTAINER`) is the only place the Linux drivers meet real binaries: the
  notifier's message is asserted off the session bus via `dbus-monitor`, kitty is driven under Xvfb. Anything
  a container cannot host — IntelliJ, the AppleScript raise, the Warp URI scheme, the real `claude` — stays
  NAMED as uncovered rather than faked.
- Unit tests: `cd orchestrator-backend && ./gradlew test`. EVERY fixed bug gets a regression unit test
  (sob-ai:unit-testing rules), verified RED by actually reverting the fix and running the test.
- E2E matrix: `./gradlew e2eTest` (own source set `src/e2e/java`, NOT in `test`/`check` — it needs git + tmux
  and drives real worktrees, so the fast hermetic gate stays fast). It runs the flow once per `TaskFlowCase`
  with `orchestrator.agent=stub` (`StubAgentRuntime` — the ONE non-deterministic participant replaced; every
  GUI driver is a Mockito double) and asserts an exact end state. Two rules it lives by: widening coverage is
  adding a ROW to `TaskFlowCase.matrix()`, and a combination that is NOT covered is named there with the
  reason — a silent gap reads as coverage. Cleanup kills tmux sessions BY PREFIX, because `tab-per-task`
  creates `<session>-<taskId>` ones the configured name alone would leave behind.

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
- Default run is the WEB BOARD: `./gradlew build stageJar` then `java -jar build/libs/jagt-run.jar` serves it
  on 8290 and prints the URL. Run the STAGED copy — `bootJar` rewrites `jagt.jar` in place (see the gotcha at
  the end of this section), and `RunningJarWatch` exists because that symptom cost two debugging sessions. For the console instead, add `--orchestrator.ui=tui` (or `=both`) — and note the layout
  smoke script must pass that flag too, since it drives the TUI (bootJar has a
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
  harmless: the OLD instance dies, just restart from the freshly built jar. The SAME cause has a second face
  that looks nothing like it: a jagt that keeps RUNNING while you rebuild answers 500 on whatever it had not
  loaded yet (`/status`, `/stats`, `/orphans` first, while the board still renders) — diagnosed twice as an
  endpoint bug before the inode was checked. Avoid both by running the staged copy (`./gradlew stageJar`,
  then `build/libs/jagt-run.jar` — a symlink to a per-build `jagt-run-<stamp>.jar`, so re-staging while an
  instance runs cannot touch the inode it holds; a fixed staged name had the same bug and reproduced it once);
  `service/RunningJarWatch` reports it when it happens anyway. (Past sessions
  burned hours chasing this as a logback/preload bug — it is not.)
