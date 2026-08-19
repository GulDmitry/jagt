# jagt — Stateful Multi-Agent Dev Orchestrator

Local orchestration of AI coding-agent CLI sessions across isolated Git worktrees. macOS-first (kitty +
IntelliJ IDEA via the `idea` CLI), Java 25 / Spring Boot 4.x — but every OS- and agent-specific piece sits
behind a strategy interface (see PLUGGABLE BY DESIGN), so a Linux port is new driver impls, not a fork.
Jackson is v3 (`tools.jackson.*` packages, unchecked exceptions); annotations stay `com.fasterxml.jackson.annotation`.
Build tool: Gradle, Groovy DSL only (wrapper committed). Never introduce Maven or Kotlin (incl. `.kts`).
`ARCHITECTURE.md` is the map — what KINDS of thing jagt has and where a new one goes, with each kind's state on
it. Read it before adding a kind of thing this file has no rule for. THIS file is `AGENTS.md`; `CLAUDE.md` is a
link to it, because no file here is named after one vendor.

## Components
- `orchestrator-backend/` — Spring Boot app ("The Brain") AND the Master console itself: state manager,
  Git lock, MCP HTTP server (`POST /mcp`), Watchdog, auto-review scheduler, macOS automation (osascript).
  Run the jar in a real terminal (see Build & run) — the process IS the Master TUI.
  Outside writes are the sub-agent's job via its own MCP (push, merge request, review replies) — the ONE
  exception the backend may ever do itself is opening a task's review request over `CodeHost` (`ShipService`,
  and only with a host configured). Outside READS have two paths — a one-shot headless agent
  that inherits the human's own MCP (see Master assistant), and, when configured, the read-only `CodeHost` /
  `Tracker` seams (see PLUGGABLE BY DESIGN). Both are opt-in and need a token in the environment
  (`orchestrator.code-host.*`, `orchestrator.tracker.*`); with neither configured the backend holds no
  credential at all.
- HOW AN AGENT REACHES THE MCP SERVER IS PART OF THE `AgentRuntime` SEAM, and there are exactly two paths
  (`adapter/agent/McpEndpoint` documents both): HTTP — the CLI is pointed at `orchestrator.mcp-url` and carries
  `X-Working-Directory: <worktree>` itself, nothing running in between; or stdio — the CLI can only SPAWN a
  server, so the runtime calls `AbstractAgentRuntime.linkStdioProxy` and gets `mcp_client.js`, the standard
  Node bridge that POSTs the same header. Prefer HTTP: verified against a real session, and it is what took
  Node out of jagt's requirements. `mcp_client.js` exists only for the stdio path (Codex today, whose config
  has no verified remote-server form) — do NOT link it for everybody again. A LIVE session survives a backend
  restart on the HTTP path (measured 2026-08-17 against a real Claude session): the server keeps no session id,
  so the next tool call simply reaches the new process, and a call that failed with the backend down does not
  retire the server for the rest of the session — the very next one succeeds. The stdio proxy's
  `ECONNREFUSED` retry is therefore not what a restart depends on.
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
  moved TO, with when and WHO ASKED, oldest first, capped at 50 (the file is rewritten on every MCP call). A
  KEEP-ALIVE adds nothing (same status = no entry, else four real transitions drown in hundreds of identical
  rows), and a task starts its history at the status it was created with. The asker (`task/ActionOrigin`) is
  carried by `service/OriginContext` and stamped in `StateService`, NOT passed down: a deploy reaches the same
  code whether it was clicked, typed, said in words or called over MCP, so every signature in between would
  have to grow a parameter it has no use for. Set it at an ENTRY POINT only — `surface/board/OriginFilter` (both HTTP
  surfaces at once, so a new endpoint cannot forget), `GrammarDispatch.run`, `NaturalLanguageDispatch` and
  `AutoReviewScheduler`; nesting is honest, so console free text is recorded as the interpretation it became. Read "since when in this status" from
  `TaskState.statusSince()`, NEVER from `lastActiveTimestamp` — a keep-alive bumps that one, so an hour-old
  status would look fresh.
  Status enum: NEW, IN_PROGRESS, REVIEW_PENDING, SHIPPING, CI_POLLING, CI_FAILED,
  REVIEWED (nothing unresolved + CI green), APPROVED (a human actually approved the review request),
  DEPLOY_CONFLICT (deploy hit a merge conflict — human resolves it in the deploy worktree), DEPLOYED,
  REVERTED (its deploy was taken back out; the branch and commits survive, so the next move is a fix), DONE.

## Session roles
- Master = the backend process itself. `MasterShell` owns the screen; `surface/console/GrammarDispatch` parses the fixed
  grammar and executes it in-process: no LLM, no MCP round-trip, no tokens, no drift. There is NO Master Claude session — the
  deterministic REPL/TUI replaced it, and `master_prompt.md` went with it (see git history). The only LLM
  call on the master side is the headless one-shot assistant below.
- Sub-agents: Claude in worktrees `<taskId>-<projectKey>` (sibling of the base repo). Their generated
  `CLAUDE.md` carries full system knowledge (orchestrator root, all projects, active tasks) plus per-task
  rules; instructions arrive via `task_context.md`.

## Control surfaces (web board + console)
- TWO front-ends, ONE core, and the seam is `OperatorUi` (`…surface.ui`, selected by `orchestrator.ui`: web | tui |
  both — default WEB). `OperatorUiRunner` is the only `ApplicationRunner`; a blocking surface (the TUI, which
  owns the terminal) starts last so the board is already serving. Adding a surface must not add a second
  answer to any question the others already answer:
  - "what is this task and what can I do with it" is `flow/Move` + `flow/TaskView`, built by
    `service/TaskViews`. The TUI, `/status` and `/api/tasks` all render THAT. `Move.shippable` is also what
    `ShipService.requireShippable` calls — the dashboard used to advise independently of the gate, which is
    exactly how they drifted apart.
  - PARITY IS AN INVARIANT, not an aspiration: a capability that exists in ONE surface only is a bug. Per-task
    verbs come from `Move.actions()`, so a new action appears on both at once — GROUPED there too
    (`TaskAction.Group`: FLOW moves the task on and closing it counts, TOOL only looks at it or restarts the
    agent), and `Move` SORTS by that group rather than trusting the order somebody appended in, so a new verb
    lands on the right side of the card by declaring its group and nothing else. The board renders one row per
    group and reads which groups exist off the wire — a page that knew the names would be a second answer. Shared text lives in
    `command/CommandReference` (the grammar) and `command/StateViews` (dashboard + stats), so neither surface renders its
    own version. The reports open in a `<dialog>` over the board, never a new page — and EVERY dialog closes three ways: Escape, its own button, and the dimmed area around it, which is the click a human makes first. The backdrop close is guarded by where the press STARTED, so dragging a selection out of a report does not dismiss what is being read. ONE deliberate exception to
    parity: `quit` is console-only — stopping the backend belongs to whoever owns the process (Ctrl-C / kill),
    not to a browser button, and nothing is lost by that since agents live in tmux. A shutdown endpoint was built and removed;
    do not add one back.
  - "WHAT COMMANDS EXIST" HAS EXACTLY TWO ANSWERS, AND BOTH ARE DECLARATIONS. A verb a task owns is a
    `flow/TaskAction` row, gated by `Move`, executed by `CommandService`. A verb no task owns is a
    `command/GlobalCommand` bean (`command/*`, collected by `GlobalCommands`): id, hint, usage, whether
    its answer is a REPORT, whether it is console-only. `CommandReference` RENDERS both — `help`'s text and the
    palette's verb list — so a hint is written once; `GrammarDispatch` LOOKS A TYPED WORD UP in the two instead of
    switching on it; and `GET /api/commands/{id}` serves any report, so declaring another one needs no endpoint, no
    console arm and no button in the page (the board BUILDS its report buttons from that list). That is what parity
    failed on before (2026-08-19): the per-task verbs always had this shape, and
    `do`/`resume`/`stats`/`activity`/`help` were hand-written in six places each — which is exactly why `resume`,
    `stats` and `help` were console-only until 2026-08-13 and `activity` until 2026-08-18. Three deliberate limits:
    that endpoint refuses anything that is not a report (a GET must not be able to start a task), a console-only
    command is filtered out of what the board is told at all, and tier 2 stays narrower on purpose — a prose
    request cannot ask for a dialog, so `NaturalLanguageDispatch` names the two launches itself and offers no
    report.
  - THE EMBEDDED TERMINAL IS A RENDERING OF `focus`, NEVER A SECOND VERB. With `orchestrator.web-terminal
    .enabled` a Focus click on the board also opens the task's tmux session in a `<dialog>`:
    `adapter/TtydWebTerminal` serves ONE ttyd per tmux SESSION (not per task — a task is a window inside one),
    and `POST /api/tasks/{id}/terminal` hands back its address, `null` meaning none is configured. It selects
    no window and executes nothing; the action itself still goes through `CommandService`, so the console keeps
    raising the native viewer and the card grows no button outside `Move.actions()`. Four things it owes, none
    of them optional: the terminal is WRITABLE, because a view you cannot answer the agent in is pointless — and
    a writable terminal is a SHELL, so `--check-origin` is what makes the served page the only origin that may
    open a socket (a websocket handshake is exempt from same-origin rules, so without it any page the human
    visits can drive the agents' session over loopback; the bind address is NOT that defence and never was).
    `--exit-no-conn` ends the server with the last viewer, so a `done` that kills a tmux session cannot leave a
    ttyd behind and no port leaks; the port is the first FREE one from `web-terminal.port`, so a server orphaned
    by a `kill -9` moves the next one along instead of killing the feature. The frame is UNLOADED on close,
    since tmux sizes every window to its smallest attached client, including one nobody is looking at. And ttyd
    stays ONE class, not a sixth seam — a second web terminal is an interface extraction, and nothing outside
    it names ttyd.
  - THE MACHINE IS ONE FILE AND IT HAS TWO DOORS. `flow/FlowRules` is the whole life of a task: which statuses
    allow which action (the guard reads `flow/Facts` — an open request, and a liveness probe the projection passes
    as "no" because it costs a process spawn per row), and what each outcome of that action leads to. Door one is
    `flow/FlowEngine.run`: check the rules, run the `capability/TaskCapability` registered for the action, write
    the status the table gives for its `flow/Outcome`. Door two is `flow/FlowReports`: a status the task itself
    reports — its agent over MCP, or a round jagt read for it — refused unless `FlowRules.reportable` allows it,
    which is what stops a task talking itself onto a shared branch, out of one, or closed. NOTHING BELOW `flow/`
    NAMES A STATUS: a capability does the work and reports OK / RELAYED / CONFLICT / PARTIAL / GONE plus the
    sentence and the stamp, so the same work can be reached from several statuses without every doer learning the
    machine. `withStatus` therefore appears in `flow/` and in the record that implements it, nowhere else — that
    is greppable, and it is the invariant. PARTIAL is the one outcome that REFUSES: it is stamped on the task
    first and thrown second, because a shared branch holding half a change must be recorded, not merely
    complained about. The table stays Java rather than config: every status and action in it is checked by the
    compiler.
  - "how is an action executed" is `service/CommandService` (validates against `Move` first, so a stale board
    tab is refused with a sentence, not with a git error three layers down), and "how is a task started" is
    `service/TaskLauncher`. The console parses a command line, the controller parses JSON; neither owns rules.
    The sentence stays the whole answer for a human; a refusal a caller must ACT on also carries a
    `flow/Refusal.Code`, and that enum grows ONLY when something branches on the new value — a reason
    nobody handles differently keeps throwing plain.
- THERE IS NO TOOLS FACADE ANY MORE, and do not bring one back. `OrchestratorTools` grew to 871 lines and
  eleven collaborators, and every attempt to thin it ADDED one, because a delegating aggregate keeps what it
  does not shed. It was DISSOLVED (2026-08-14): each MCP tool group declares its own tools (`surface/mcp/McpTools` +
  `surface/mcp/McpToolRegistry`, implementations under `surface/mcp/tools`), and every other caller takes the small service it
  actually uses — `AgentSessions` (tmux window, focus, kill, relay), `TaskProvisioning` + `WorktreeSetup` +
  `SubAgentBriefing` (creation), `AgentStatusReports` (what an agent reports), `IdeLauncher`, `DeployService`
  (the only shared-branch writes), `TaskRetirement`, `TaskResume`. The per-task verbs are a class each under
  `capability/`, reached through the flow engine. `surface/mcp/CallerScope` owns the X-Working-Directory rule for
  all of them.
- TWO-TIER DISPATCH: tier 1 is the grammar (typed command / board button) and it stays LLM-free. Tier 2 is
  `service/NaturalLanguageDispatch` — free text (an unknown console line, or the board's ⌘K palette →
  `POST /api/interpret`) goes to a model that only PROPOSES one grammar command; the dispatcher validates the
  task exists and the verb is real, then executes through `CommandService`, so tier 2 can never do more than
  a button. The call is deliberately stripped (`--strict-mcp-config --mcp-config '{"mcpServers":{}}'`, no
  `--setting-sources`): text→command needs no tools, and a loaded MCP server would be paid for in context.
  It answers with the interpretation FIRST ("understood as `ship a1` — …"), and a single unknown word is a
  typo, not a request — it never reaches the model.
- A RENAMED VERB KEEPS ITS OLD SPELLING, AND ADVERTISES ONLY THE NEW ONE (`sweep`, typed as `review` since
  2026-08-18). ONE map owns it — `TaskAction.RENAMED`, read through `byRetiredVerb` — and EVERY surface where a
  human types has to consult it, or the promise is a lie in the one place it was made: the console's grammar,
  the palette (`CommandReference.Verb.aliases`, which the page matches and offers nowhere) and a tier-2
  proposal that echoed the word. Two things it deliberately is NOT: `byId` stays STRICT, because that one
  answers a URL segment and a retired verb is not a wire id; and the lookup resolves retired spellings ONLY,
  never a current id — the grammar's verb set is the switch, so `diff …` keeps reaching the model as free text
  instead of being parsed as a verb it never was. `CommandReference` names just the current verb: two spellings
  in `help` are two answers to one question.
- NO LIMIT ON CONCURRENT TASKS, and this is a DECISION, not an omission. A cap (`agent.maxConcurrentTasks`
  + `TaskAdmission`) was built and then REMOVED on the owner's instruction: jagt runs on other people's
  machines, one of which has 100 GB of RAM, so a number picked here is wrong for almost everyone and refusing
  a `do` on that basis is jagt deciding something it cannot know. Whoever wants a bound has the machine's own
  tools for it. Do not reintroduce a cap, a queue, or a "slots" indicator.
- `Phase`/`Owner` are a PROJECTION for humans, never persisted and never a second state machine: `TaskStatus`
  stays the SSOT. Eleven statuses collapse into six phases because four of them read as the one word "review".
- Liveness is deliberately NOT an input to the projection (a tmux probe per task per render); a task stuck at
  SHIPPING is therefore offered SHIP and the gate refuses at execution time if its agent is alive.
- THE BOARD LISTENS ON LOOPBACK (`server.address: 127.0.0.1`), because it asks for no password and can deploy,
  close a task, start an agent and — with the web terminal on — hand out a writable shell. Widening it is a
  config line the human writes themselves, and ttyd was already bound this way; a board on 0.0.0.0 with no auth
  was the asymmetry.
- The board is vanilla HTML/CSS/JS under `src/main/resources/static` — NO build step, NO CDN, no external
  asset of any kind (it must work with the machine offline and stay inside the one jar).
- NEITHER surface polls for state: `StateService.onChange` is the one event both use — `TaskEventStream`
  forwards it as SSE, and `MasterShell` sets a dirty FLAG its render loop consumes (Lanterna's screen belongs
  to the UI thread; the listener runs on whichever thread served the agent's MCP call — never paint from
  there). The SSE event carries no payload on purpose: a payload would be a second serialization that could
  disagree with `/api/tasks`. The periodic tick survives in both only for the relative "ACTIVE" clock.
- UNATTENDED WORK IS A DECLARED KIND, never a schedule a class keeps to itself: `job/Job` (id, one line of what the
  human gets, an interval or `null` for once at startup, `run()`) and `job/Jobs`, the ONE ticker — each run on its
  own thread, never overlapping itself, a run that throws booked against that job and nothing else, so no job needs
  a guard or a catch-all of its own. A hidden `@Scheduled` cannot be listed, reported on or validated, which is the
  point: the `jobs` report (a `GlobalCommand`, so both surfaces show it) names each job, its cadence, when it last
  ran and when it runs next — work nobody watches is visible BEFORE it acts, not only after. An adapter's own
  workaround is a job THAT ADAPTER contributes (the IDEA recent-projects cleanup), never a permanent timer for
  everybody.
- WHAT JAGT DID UNATTENDED IS READ BACK FROM ITS OWN LOG, never from a second store: `command/ActivityReport`
  tails `logging.file.name` (structured ECS JSON), keeps the entries that carry a `task` key-value and renders
  them newest first for the `activity` verb and the board's Activity dialog. The convention it depends on is the
  one already in force — INFO for work nobody watched, nothing for a button a human pressed — so an in-memory
  ring buffer or a jagt-owned log file would be a second answer to "what happened" AND would not survive the
  restart after which a human looks. It deliberately shows only work that named a task: `state.json` history
  already carries the status transitions with who asked for them. ONE RUN, ONE LOG: `surface/ui/SessionLog` empties the
  file and deletes the archives beside it before the appender opens it, so the report is this session's work and
  nothing older — the owner's call (2026-08-18), and the reason nothing gzipped is read back. The file stays
  structured on EVERY surface: `ConsoleLogging` used to try blanking `logging.structured.format.file` for the
  console UIs, which would leave `activity` nothing to parse, and that dead override is gone.
- THE CHECKS ARE READ WHERE THE COMMENTS ARE, AND SHOWN WITHOUT BEING ASKED FOR. A sweep already pulls the review
  round, so it stamps what the host said about the pipeline onto the task (`TaskState.pipelineStatus`, the host's
  OWN wording) and `flow/Pipeline` is the one parser that turns it into GREEN / RED / RUNNING / NONE — every host
  words it differently, and two surfaces matching on words would agree by luck. The board shows one dot in the
  card's meta row and the console prefixes the request line (`CHECKS RED · …`), because a red run while the task
  still reads CI_POLLING is exactly what a status word cannot show. The human is tapped ONCE per run, on the
  transition INTO red: an unattended poll that notified every time would be a loop, and a red run that is already
  known is not news.
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
- A BRANCH THE BASE REPOSITORY STILL HOLDS IS FREED, NOT REFUSED (`GitService.freeCheckout`): git allows one
  checkout per branch, nobody works in the base repository, and a task blocked on a checkout nobody remembers
  making is worse than a WARN naming what it was on. Four things that are not incidental: it detaches the
  repository IN PLACE — no other ref, so the files an editor has open do not change under it, and a per-task base
  with no local branch is no obstacle; it runs INSIDE the recreate/resume arms, never before the strategy switch,
  because a refusal must leave the repository where it was; the detach is UNDONE when what it was freed for does
  not land, in `createWorktree` and again in `TaskProvisioning`'s unwind (a resumed branch survives, so there is
  something to go back to); and it ignores UNTRACKED files, since only tracked changes are carried. Two cases
  stay refusals: tracked changes in that checkout, and a branch held by ANOTHER worktree (another task's).
- CRITICAL git safety: the ONLY writes to a shared branch anywhere are `deploy` (task branch ->
  `deployBranch`, via `GitService.mergeIntoAndPush`) and its undo `revert` (`revertMergeAndPush`: reverts the
  merge commit deploy recorded, ADDS a commit, never rewrites history, never force-pushes). Both are
  Master-only and both go through `deployTarget`, so they share one deployBranch guard. `revert` refuses
  rather than guess in every ambiguous case: no recorded merge commit (a deploy from before `deployCommit`
  existed — the human gets the by-hand `git revert -m 1` recipe, jagt will NOT search the log), the commit is
  not on the branch, it was already reverted, or the revert conflicts (aborted + cleaned up; unlike a deploy
  conflict there is no half-state worth keeping). `ship` creates/updates a merge REQUEST only —
  never merges. WHAT A REVIEWER SAID IS NOT A GATE ON `deploy` (owner's call, 2026-08-18): `Move.deployable` asks
  only whether a request is open — plus DEPLOY_CONFLICT, which is finished by deploying again — because deploy
  merges the task BRANCH and git's only precondition is commits on it. Gating the button on REVIEWED/APPROVED
  meant a human looking at a REVIEW_PENDING card could not land a request they had decided to land. What stays
  excluded is what could only race or refuse: NEW (nothing on the branch), SHIPPING (a push in flight),
  IN_PROGRESS (an agent committing INTO the branch this would merge), REVERTED (a revert ADDS a commit, so the
  branch holds nothing the deploy branch lacks — the answer could only be "nothing to deploy"), DONE. The BOARD
  names the writes it is asking for before it makes them — one `project → branch` line per repository, read from
  `TaskView.RepoView.deployBranch`, because "the deploy branch" is not something a human can check.
  The base branch (`baseBranch`, tasks are cut from it) is READ-ONLY: nothing ever
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
  NO BULK BRANCH CLEANUP, and this is a DECISION, not an omission: `prune [all]` (a cross-project sweep of
  local branches merged into `deployBranch`) was built and then REMOVED on the owner's instruction — branch
  cleanup belongs to the ONE task it concerns, not to a command that reaches across every project at once, and
  a human who wants a branch gone has git. Do not reintroduce a prune verb, a "merged branches" report, or a
  board button for either.
- Watchdog scope is deliberate (`WatchdogService.watches`): it alerts only for statuses where the AGENT is
  expected to be working — NEW, IN_PROGRESS, SHIPPING. Every other status idles by design (CI_POLLING waits
  on the code host, REVIEW_PENDING/REVIEWED/APPROVED/DEPLOY_CONFLICT on the human), and watching those turns
  the alert into noise.
- ONE review sweep per task at a time, whatever triggered it: the guard lives in `ReviewSweepService` because the
  manual `sweep`, the auto-poll and any future UI button all pass through it (two sweeps = the headless read paid
  twice + two briefs relayed for one review round). The other problem — ticks QUEUING behind a sweep that runs
  minutes — belongs to `Jobs`, which never runs a job concurrently with itself, so `AutoReviewScheduler` keeps no
  guard of its own.
- All git ops in `GitService` under a per-repository `ReentrantLock` (index.lock races are per-repo;
  a slow fetch in one project must not block another).
- Sub-agents can only act on their own task (X-Working-Directory scoping is ENFORCED in
  `surface/mcp/CallerScope`, and its wiring into each tool is what `McpToolScopeTest` pins — the rule was real for three
  tools and MISSING from four until 2026-08-14, so a new tool taking a taskId gets a row in that test, not a
  promise); `initialize_task`/`remove_task`/`deploy_task`/`revert_task` are Master-only. Task ids are validated
  (`[A-Za-z0-9][A-Za-z0-9_-]*`) — they become branch/dir/tmux names. A task's OWN repositories are ONE scope, not
  several: `StateService.findByWorktree` answers from any of them, so a multi-repo task stays one caller however
  many worktrees it holds — narrowing that back to the primary worktree silently breaks every tool the agent
  calls from a sibling repo.
- ONE SESSION, MANY REPOSITORIES — what multiplies is WORKTREES, never agents. A task holds a LIST
  (`task/TaskRepo`, `repos.get(0)` = where the session runs) and every per-repo step iterates it: creation cuts
  a worktree each (`TaskProvisioning.resolveRepos` validates ALL of them before cutting ANY, and a failure part
  way unwinds the ones already cut), `ship` commits/pushes/opens a request per repository against THAT
  repository's own base branch, `done` deletes every worktree — the siblings hold checkouts and copied secrets
  nothing else would remove. Three rules that are not obvious from the loop:
  - The review round is MERGED, and it answers as the least finished repository (`ReviewSweepService.merged`):
    approved only when all are, the pipeline the single WORST one — never a concatenation, which reads as
    "success" to the caller's own check — and each comment prefixed with the repository it came from. Reading
    only the session's request would let a green half advance the whole task.
  - `ship` is all-or-nothing about hosting: one repository without a `CodeHost` sends the WHOLE task down the
    prose relay, because half pushed by jagt and half asked of the agent is a state nobody can describe.
  - `deploy` LANDS IN ORDER AND STOPS AT THE FIRST CONFLICT, and the sentence names BOTH sides — what is live on
    the deploy branch and what is not. A shared branch cannot be written atomically whatever jagt does (it can
    move between a check and a push), so the honest half-state beats a dry run that only makes the same failure
    rarer at twice the merges. Every repository is checked deployable before the FIRST push, and the half-state is
    read from WHERE THE SEQUENCE STOPPED, never from the recorded merge commits — those outlive the round that
    made them, so after a second ship every repository would read as live. SIBLING REPOSITORIES DERIVE THE SAME
    DEPLOY WORKTREE PATH (`<taskId>-deploy`, next to the repository), so the directory alone never decides
    anything: `GitService.hasDeployWorktree` asks git who cut it, `mergeIntoAndPush` REFUSES to finish a worktree
    another repository owns (it would push that repository's work to this one's remote), and only a task handed
    back at DEPLOY_CONFLICT resumes at one — a leftover from any other round would make the deploy skip the
    repositories before it and still call the task deployed. NOTHING TO DEPLOY IS NOT A FAILURE
    (`GitService.NothingToDeployException`): a repository whose branch adds nothing — never touched by the change,
    or already on the branch — is passed over and named, which is also what makes starting the sequence over
    harmless when no worktree answers. A stop for any OTHER reason leaves the status alone (there is nothing to
    resolve in a worktree) but still names what landed. `revert` walks back the other way: reverse order, only the
    repositories that have a merge commit, each one FORGETTING it as it comes out, so a repeat touches only what
    is still live — and REVERTED is set only when everything that landed is out. Both half-states are STAMPED on
    the task, not just thrown: a sentence in a console nobody scrolled back to is not a record of a shared branch
    holding half a change.
- The MCP transport must never emit non-JSON-RPC bytes: malformed JSON → `-32700` from the controller,
  HTTP errors → synthesized JSON-RPC error in `mcp_client.js` (never forward Spring error pages).
  The proxy retries ONLY `ECONNREFUSED` (request never sent) — other failures may have executed a
  non-idempotent tool.
- `state.json` writes are atomic (temp file + `Files.move` ATOMIC_MOVE) in `StateService`. Atomicity covers a
  TORN file, not a BAD one, so every write also copies the previous version to `state.json.bak`, and a read
  that cannot parse the primary recovers from that backup (moving the bad file to `state.json.corrupt`). With
  no usable backup it THROWS: starting with an empty task list over an existing state file would destroy the
  human's data on the next write. Never make that path "fail soft".
- `WorktreeOrphanScanner` only ever LOOKS: worktree directories no task owns can hold uncommitted work AND copies
  of secrets (`worktree.copyGlobs`), so it WARNs one line each at startup, plus one desktop ping, and deletes
  nothing. NO surface offers it — the board dialog and `GET /orphans` were removed on the owner's instruction
  (2026-08-18), and the console never had a verb for it: housekeeping is not something a human acts on mid-flight,
  and the board is dense enough. Do not add either back. It is a job with no interval (once, as soon as the
  application is up) and it catches nothing itself: a throwing run is booked against that job by `Jobs`, because a
  diagnostic must never be able to stop the backend from starting.
- WHAT IS MISSING IS SAID AT STARTUP, NOT AT THE CLICK THAT NEEDED IT. `startup/StartupValidation` asks every
  `StartupCheck` before the operator surfaces open and refuses the start with ALL problems at once
  (`Misconfigured`, printed by `StartupFailure`) — a human fixes one list instead of one item per restart, and
  each line names the key that fixes it. A check lives NEXT TO the part it answers for, so it exists only when
  that part was SELECTED and nothing branches on which terminal, agent or host is configured (`CliEditorDriver`,
  the kitty driver, `TtydWebTerminal`, `LibNotifyNotifier`, `CodexAgentRuntime`); what no implementation can
  answer for — a `type` that selects NOTHING, the human's `config.json`, jagt's own paths, git and tmux — is a
  check in `startup`. Two limits are DECISIONS, not gaps: nothing reaches the NETWORK (presence, never validity
  — a wrong token is the first read's answer, and a laptop offline must still start), and nothing asks a remote
  about a branch (that is a fetch per project on every start). `orchestrator.startup-checks=false` belongs to
  test harnesses ONLY — what the checks ask about is the human's machine, and a runner is not one, so every
  suite and smoke script that boots the app passes it exactly as it passes `open-warp-window=false`.
- Every MCP tool call from a registered worktree bumps `lastActiveTimestamp` (Watchdog keep-alive).
- Tomcat's "Error setting socket options" (`SocketException` at `setSoLinger`) is a connection the peer aborted
  between `accept()` and configuring it — a browser pre-connect, the losing half of a Node client's IPv6/IPv4
  race to `localhost`, a `curl` probe. `SO_LINGER` is simply the first unguarded setsockopt, and Tomcat gives no
  knob (`AbstractProtocol` sets `connectionLinger` in its constructor). `surface/board/AbortedConnectionFilter` drops that
  one event and nothing else — do NOT "fix" it by silencing `NioEndpoint`, which also hides real socket errors.
- CODE REVIEW IS NEVER FULLY AUTOMATED. The auto-review poll (`AutoReviewScheduler` → `ReviewSweepService`)
  only READS and DRAFTS: an approval may advance status, but comments are merely RELAYED to the agent, which
  fixes LOCALLY and writes its intended answers to `review_replies.md`. Nothing is pushed or posted without
  an explicit human `ship`; the loop never ships, deploys, pushes or posts on its own. Every round hands the
  human two artifacts to inspect via `ide <alias>` — the local diff and the drafted replies. Do not erode
  this: the human-in-the-loop gate lives in the OUTCOME, not in who triggered the sweep.
- WORK THAT RUNS UNATTENDED MUST BE VISIBLE WHILE IT WAITS, not only after it acts. `AutoReviewCadence` is the
  WHOLE auto-review policy — enabled, the interval ramp, AND `watch(task, now)` answering what a human is owed
  about one task (`task/AutoReviewWatch`: watching + the absolute next-poll stamp, window elapsed, off for this
  task, or nothing). `AutoReviewScheduler.decide` is a translation of that same watch, so a card cannot promise a
  poll the scheduler will not make. Both surfaces show it — the console's dashboard header carries
  `cadence.summary()` and each task a `└ auto-review:` line; the board has the chip (`Board.autoReview`) and a
  per-card line. Whether polling runs at all is a property of the INSTALL, so it is stated ONCE per surface and
  never repeated per card; the one per-task exception is a task whose own `autoReview` is false while the install
  polls, which would otherwise sit still with nothing saying why. The countdown is an ABSOLUTE stamp on the wire
  and formatted per surface (`DurationFormat.countdown` / the page's own mirror), exactly as the two clocks on a
  card already are — a remaining-duration would be stale the moment it was fetched. It is a FLOOR, not a promise:
  the scan runs every 60s, so a poll shown as due happens within the next tick. One more rule the surfaces share:
  `TaskViews.snapshot()` reads the configuration ONCE per render and hands back the tasks WITH the policy that
  explains them — the console redraws on every keystroke, and two reads could disagree inside one frame.
- A REVIEW ROUND IS A JUDGEMENT, NOT A WORK ORDER. Relay a bare list of comments and the agent implements all
  of them — including the ones wrong about the architecture, which the reviewer could not see from the diff —
  and the human then reads agreement into code that was only obedient. `ReviewSweepService.brief` therefore
  opens with the three routes per comment (fix / change NOTHING and say why / ask via `awaiting:` before
  guessing), and `sub-agent-context.md` carries the same stance for the task itself. A question ENDS the round
  (REVIEW_PENDING, message `awaiting: …`) instead of parking in CI_POLLING — a parked task is re-briefed by
  every auto-review poll on the very comments it was told to hold, paying for a review read each time.
  Deliberately NOT extended to jagt's orchestration steps: a commit/ship instruction IS the human's approval
  and is executed as given.
- A ROUND REPORTS ITS OUTCOME, because all three end at REVIEW_PENDING and the human is advised from the
  MESSAGE: `awaiting: …` = a question, `no changes: …` = nothing was edited (already handled, or every comment
  pushed back on), anything else = there is a diff to read. `flow/AgentReport` is the ONE parser of that
  vocabulary (`Move` and `DashboardLine` both read it, so they cannot disagree), and `Move` is total over
  (status × report). Why it matters: advising SHIP for a no-change round is a LOOP — the ship commits nothing
  and returns the task to CI_POLLING, the only status the auto-poll watches, which relays the same threads
  again. So NO_CHANGES highlights nothing and says the open threads are the reviewer's move.
- A REPLY DOES NOT RESOLVE A THREAD, and the sweep relays every UNRESOLVED one (`resolvable && !resolved`), so
  a comment the agent pushed back on comes back every round forever. The agent therefore resolves — at SHIP
  time, with its own MCP, never jagt's `CodeHost` — ONLY the threads whose code it actually changed
  (`ShipService.repliesStep`). A thread it disagreed with or asked about stays unresolved: that disagreement is
  the reviewer's to settle, and resolving it would read as agreement. During the round the agent posts nothing
  at all — `review_replies.md` holds DRAFTS until the human ships.
- NO GIT HOOKS, EVER — never propose, add, or rely on any git hook anywhere; enforce invariants in code + prompts.
- A DETACHED LAUNCH GETS ITS OWN SESSION, NEVER AN IGNORED SIGNAL. `ProcessBuilder.start()` does not leave
  jagt's process group and the terminal delivers Ctrl-C to the whole GROUP, so stopping the backend used to
  SIGINT the IDE jagt had started — one IntelliJ process hosts EVERY project window (measured 2026-08-18: same
  pgid, child dead on SIGINT). `ProcessRunner.detachedFrom` therefore runs the command under `setsid`, or under
  `perl -MPOSIX -e 'POSIX::setsid(); exec @ARGV'` where there is no `setsid` binary (macOS ships none). The
  first attempt was `sh -c "trap '' INT QUIT HUP; exec …"` and it is the WRONG fix, do not go back to it: an
  ignored disposition is inherited by every descendant, so the IDE's own Stop button (a SIGINT), Ctrl-C in its
  embedded terminal and `kill -QUIT` thread dumps all stopped working for everything it spawned. Both wrappers
  `exec`, so the returned `Process` is still the app and `destroy()` reaches it. Agents were never at risk (the
  tmux server is already its own session) and kitty daemonizes itself with `--detach`; what WAS at risk is
  everything started through `runDetached` — the editor and ttyd. A wrapper that always starts also means a
  missing binary is no longer an `IOException`, so `runDetached` FAILS the launch when the wrapper exits
  non-zero at once — without that, no ttyd installed reads as "no web terminal configured".
- NO GUI/keystroke automation, ever: System Events keystrokes race with the human typing (they land in
  whatever is focused). Agent terminals are windows in a session host (`port/SessionHost`, tmux today); visibility comes from one Warp
  window opened via `open warp://launch/jagt-agents` (launch config generated into
  `~/.warp/launch_configurations/`) whenever `tmux list-clients` shows nobody attached.
- PLUGGABLE BY DESIGN — this is a FIRM architectural invariant, do not erode it. jagt targets Linux +
  macOS with SWAPPABLE terminals, notifiers, editors, and AI-agent runtimes (Claude Code / Codex / Qwen /
  … — any MCP-capable CLI). Everything OS- or agent-specific lives behind a STRATEGY INTERFACE, selected by
  config, so adding a new one is "implement the interface + register a config value" — NEVER a hardcoded
  `if claude`/`if macos` sprinkled through the flow. The agent-agnostic task flow (create worktree →
  provision → launch → talk over MCP) must stay free of any single agent's assumptions. The six seams:
  - `UserNotifier` (`orchestrator.platform`, default macos), `TerminalDriver` (`orchestrator.terminal`,
    default `kitty`; `warp` too), `EditorDriver` (`orchestrator.editor-command`) — in `…adapter` (ports in `…port`).
  - `AgentRuntime` (`…adapter.agent`, `orchestrator.agent`, `claude` default, `codex` the second impl) — the
    pluggable AI-agent CLI: `launchCommand` AND worktree provisioning (`provisionWorktree`, a template in
    `AbstractAgentRuntime` + one per-agent hook) live here. `mcp_client.js` is a STANDARD, agent-agnostic MCP
    stdio↔HTTP proxy (keep it that way) and is linked by the template; only the config that declares it
    differs per agent (Claude `.mcp.json` + `.claude/settings.local.json`, Codex `.codex/config.toml` with
    `CODEX_HOME` pointed at the worktree) and belongs in each `AgentRuntime`. Nothing outside the runtime may
    name an agent's files — `WorktreeSetup` only calls `provisionWorktree`, and `AgentSessions` `displayName`.
  - `CodeHost` (`…adapter.codehost`, `orchestrator.code-host.type`, default none; `gitlab` and `github`) — reads of a
    review request (the ROUND a sweep decides on, and the BRANCHES a `resume` adopts, so neither costs a model
    call) plus EXACTLY ONE write: `createOrUpdateMergeRequest` (opening the artifact a human then reviews).
    Never a push, a merge, a comment or an approval — those belong to the human's gates or to the agent's own
    MCP; a `CodeHost` that merges is a bug. The write is idempotent per (source, target) and
    NEVER retitles an open request (`ship` reruns every review round, and the human may have edited the title).
    `ReviewReader` deliberately does NOT fall back to the paid headless read when a configured host fails: that
    would spend money invisibly and hide the misconfiguration. A partial REST read must fail whole — "no
    unresolved comments + green pipeline" ADVANCES a task. The one caller of the write is `ShipService`, and
    only when a host is configured — with none, `ship` keeps relaying the prose to the agent.
    WHICH PROTOCOL a host speaks is ITS business, not the seam's: GitHub's read is one GraphQL query because
    thread resolution exists nowhere in its REST API, and a round that cannot tell resolved from open relays
    every comment it ever saw, forever. Two GitHub facts that a reader will not guess and that make the
    difference between advising `deploy` and advising a fix: the substance of a review usually sits in the
    review BODY rather than in inline threads (so a round read from threads alone can miss the whole request,
    and a CHANGES_REQUESTED decision must never come back with an empty comment list), and `reviewDecision` is
    only populated where the repository REQUIRES a review — on an unprotected repo it is null however many
    people clicked Approve, so the reviewers' own latest states are the fallback. Its `base-url` is the WEB root (the prefix that decides which URLs the
    host may claim) and each host derives its own API endpoints from it — github.com serves its API from
    another host entirely. Two flags have no GitHub counterpart on purpose: squash and delete-branch-on-merge
    are REPOSITORY settings there, and a `CodeHost` configures no repository. The relay LINE is shared
    (`adapter/codehost/RelayLine`), so an agent never has to learn a second format for a round.
  - `Tracker` (`…adapter.tracker`, `orchestrator.tracker.type`, default none; `jira`) — reads the ONE ticket a launch
    needs (title, labels, project) so `do <ticket>` costs no model call either. Read-only, in the strong sense:
    a tracker that transitions, comments or assigns is a bug — an issue's state is the human's to move.
    `service/TicketReader` routes it exactly as `ReviewReader` routes a host, including the no-fallback rule (a
    tracker that CLAIMED the ref owns it; paying a model to retry the same read spends money invisibly and
    hides the misconfiguration). The assistant keeps ONE thing no configured tracker can do: follow a URL into
    a tracker jagt was never pointed at. Jira is read over the `v2` API on purpose — Cloud and Data Center both
    serve it, and the three fields read here are identical in v2 and v3.
  - `JsonHttp` (`…adapter.http`) is the transport BOTH of those read over, and it is not a seventh seam: it exists so a
    host or a tracker is testable without a socket (every implementation's test drives a fake of it), and it
    carries only the verbs a create-or-update needs.
  - The shared system-knowledge file is `AGENTS.md` (the cross-agent convention, `AgentRuntime
    .SYSTEM_KNOWLEDGE_FILE`); Claude reads `CLAUDE.md`, so its runtime symlinks `CLAUDE.md` → `AGENTS.md` —
    one file, never two copies to drift. WHICH NAME IS FREE IS THE RUNTIME'S TO ANSWER
    (`AgentRuntime.systemKnowledgeFile`, asked BEFORE provisioning — afterwards jagt's own links are
    indistinguishable from a checkout): a regular file already on one of those names came out of the checkout,
    so it is the PROJECT's, and taking it costs the agent the instructions the repository ships AND makes the
    next `ship` commit the loss (jagt tracks `CLAUDE.md`; so does one configured project). Claude's answer is
    then `CLAUDE.local.md` — loaded exactly the same (verified 2026-08-18), and the one name a repository does
    not version; every other runtime REFUSES, because an agent started without the safety rules that file
    carries is worse than a task that would not start. The bootstrap prompt therefore names NO file — which
    one holds the briefing varies, and a prompt that says `AGENTS.md` is wrong exactly where the fallback
    applies. A new agent = one `AgentRuntime` impl; a Linux port =
    new `UserNotifier`/`TerminalDriver`/`EditorDriver` impls. Nothing else should need to change.
- kitty is ONE driver, not one per OS: `AbstractKittyTerminalDriver` (in `…adapter` (ports in `…port`)) holds everything —
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
- It is now the FALLBACK, not the path: `do <ticket>` needs the ticket read before a worktree/agent exists, and
  `service/TicketReader` takes a configured `Tracker` first, `ReviewReader` a configured `CodeHost` first. With
  both wired the only call left is the ⌘K palette, which is a model call by DESIGN. What the assistant keeps
  that no configured API has: it FOLLOWS A URL into a tracker — or onto a code host — jagt was never pointed at,
  which is why it stays.
  `HeadlessClaudeAssistant` (`MasterAssistant`) spawns a one-shot
  `claude "<prompt>" -p --setting-sources user,project,local --json-schema '<schema>'` (stdin
  `/dev/null` via `ProcessRunner`). It hardcodes NO MCP server or path — `--setting-sources` makes the
  child inherit the human's OWN MCP (portable, OS-independent); `--json-schema` forces deterministic
  JSON. Runs from `java.io.tmpdir` so only user-level MCP loads (no jagt project MCP → fewer tokens).
  Project is resolved by intersecting the ticket's labels with each project's `labels`
  (`TaskLauncher.projectsMatching`); the title is cached for the commit. Any failure → empty → `do`
  falls back to an explicit project. Headless `-p` does NOT auto-load plugin MCP without
  `--setting-sources` (verified: default `-p` sees zero Jira tools), and narrowing it to `project` is
  equally fatal — the call runs from the temp dir, where project scope alone resolves to ZERO MCP servers
  (verified 2026-08-13). Keep `user` in the list; the ~7k tokens it costs are what buys the tracker tools.
  INHERITING IS ALSO THE CHEAPER SHAPE, which is the opposite of what it looks like: an install may DECLARE the
  servers instead (`assistant.mcp-config` → `--strict-mcp-config`, no credential in jagt because such a file
  carries `${ENV}` placeholders), and that is a determinism knob ONLY — measured 2026-08-18,
  $0.09 cold against $0.04, because the inherited prefix rides the prompt cache the human's own sessions keep
  warm while a jagt-private one is cold on almost every call. It pins the SERVERS and nothing else — settings are
  still loaded, or a declared file's `${ENV}` placeholders and the model would stop resolving (verified). Declared
  servers lose their plugin scope in tool names, so an `allowed-tools` written for the inherited spelling silently
  stops matching, and jagt cannot detect that without parsing the declaration: it is documented, not guarded.
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
- COMMENTS GO THROUGH THE `sob-ai:commenting` SKILL, EVERY TIME, BEFORE WRITING OR EDITING ANY — no exceptions,
  including a "quick" one-liner. Its HARD GATE decides: default is NO comment, one non-obvious WHY at most.
  Deleted on sight — narration of what the code does, an argument that a change is correct (that belongs in the
  review, not in the file), how the code got this way, and a fact whose source of truth is elsewhere. jagt's
  own history is the warning: 2349 comment lines against 7027 code lines, a build file explaining how the
  dashboard renders and what a merge conflict means, and two comments still naming libraries deleted months
  before (Spring Shell, JLine). ONE MORE RULE FOR THIS REPO: a file may only speak its own layer — the build
  file knows about the build, a seam interface states its contract and never one implementation's mechanism.
- EVERY TEXT JAGT WRITES IS READ BY AN ENGINEER IN A HURRY: shortest form that still answers, lowest cognitive
  load, no story. One fact per line; a decision is the decision plus at most one clause of why, not the road to
  it. This binds command sentences, docs, prompts and commit messages alike — TODO.md was 670 lines of prose for
  40 decisions before it was emptied (2026-08-18), and the owner's complaint was that nobody can read it. A
  DECIDED decision is not a TODO: it lives in the code, with the rule in CLAUDE.md and the road to it in git
  history — TODO.md holds only what is still open, and holding nothing is its normal state. If an entry needs
  three paragraphs, the code needs the explanation, not the file.
- `USE-CASES.md` is the one-line answer per SITUATION ("the request does not target the base branch → …").
  When a case turns out to be non-obvious — or a session re-derives one that was already decided — append a
  row there instead of only fixing the code. CLAUDE.md keeps the rules; USE-CASES.md keeps the answers.
- NEVER use real project identifiers anywhere in this repo — code, tests, comments, docs, examples,
  fixtures. No real ticket keys/numbers, project names, abbreviations, or issue titles from any actual
  project. Always invent obviously fictional placeholders (e.g. `ABC-42`, "Widget layout is off"); the
  existing tests already use `ABC-N` ids — follow that.
- ENGLISH ONLY, everywhere — UI strings, placeholders, example phrases, comments, docs, test fixtures. The NL
  palette ACCEPTS any language; jagt never WRITES one but English. The single exception is functional, not
  textual: `KittyTerminalDriver`'s ЙЦУКЕН keymap (`map=cmd+м …`), where the Cyrillic symbols ARE the key events.
- Markdown and docs: aim for ~120-character lines, hard max 150; don't force awkward wrapping.
- A form field explains itself with a PLACEHOLDER, not with a paragraph parked next to its button. The `*-state`
  spans are progress/verdict slots (`reading the ticket…`, `no task “x”`) and start EMPTY — static prose there
  vanishes on the first submit (the `finally` clears it) and never comes back, which reads as a bug.
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
  `adapter/Executables` (PATH, then the known install dirs — Homebrew included, because a GUI-launched
  process has neither prefix on PATH — then the per-user script dirs, then INSIDE APPLICATION BUNDLES).
  `tmux-command` used to default to `/opt/homebrew/bin/tmux`, which made
  every task on Linux fail at "Failed to start command"; the agent CLI is deliberately NOT resolved (it runs
  inside the agent's tmux window under the human's own PATH, and the string is what they read on screen).
  `editor-command`/`editor-diff-command` are LISTS, so only the launcher is resolved and the arguments stay the
  human's; a launcher nowhere to be found fails with the config KEY to set, not with the binary they never chose.
  THE BUNDLE STEP IS WHAT MAKES THE RULE APPLICABLE TO A DESKTOP APP AT ALL: an IDE's launcher lives in
  `/Applications/<App>.app/Contents/MacOS/<name>` and lands in no bin directory, so defaulting `editor-command`
  to `idea` without it broke `ide` on the owner's machine within the hour. A resolver that cannot find a GUI
  launcher forces the absolute path back into the defaults — do not weaken it.
- ONE SET OF STEPS FOR EVERY HOST: `.github/workflows/ci.yml` and `.gitlab-ci.yml` run the same suites by
  calling the same scripts (`scripts/linux-test-deps.sh` = the package list, `scripts/with-linux-desktop.sh` =
  Xvfb + session bus + notification daemon, then the smoke scripts). A step that exists in one pipeline only,
  or a CI-only code path, is a bug: green in CI and green on a laptop must mean the same thing. Neither
  pipeline needs Docker — the container image is for macOS developers and installs from that same deps script.
  `linuxDriverTest` is gated on CAPABILITY (Linux + the binaries + a DISPLAY), never on "which harness am I in".
  THE BUILD CACHE IS FOR THE HERMETIC SUITE ONLY: what `e2eTest`/`boardTest`/`linuxDriverTest` prove is the
  MACHINE, and no machine state is in a cache key, so all three opt out (`cacheIf`/`upToDateWhen` false) — a
  restored result comes back green with nothing having run, on a fresh worktree and in a pipeline that caches
  `~/.gradle` alike.
- LINUX IS TESTABLE FROM A MAC: `scripts/linux-suite.sh` runs `test` + `e2eTest` + `linuxDriverTest` inside a
  container (`docker/linux-suite.Dockerfile`). `linuxDriverTest` (source set `src/linuxTest/java`, NOT in
  `check`, guarded by `JAGT_IN_CONTAINER`) is the only place the Linux drivers meet real binaries: the
  notifier's message is asserted off the session bus via `dbus-monitor`, kitty is driven under Xvfb. Anything
  a container cannot host — IntelliJ, the AppleScript raise, the Warp URI scheme, the real `claude` — stays
  NAMED as uncovered rather than faked. Two Linux behaviours are on that list PERMANENTLY (decided 2026-08-18,
  not a gap waiting to close): `reveal` raising the viewer above other applications, and closing the viewer.
  Both need a window manager with a human in front of it, so the `@Disabled` test in
  `LinuxKittyTerminalDriverLinuxTest` documents the lead and no pipeline pretends to cover them.
- Unit tests: `cd orchestrator-backend && ./gradlew test`. EVERY fixed bug gets a regression unit test
  (sob-ai:unit-testing rules), verified RED by actually reverting the fix and running the test.
- THE UNIT SUITE RUNS CONCURRENTLY (JUnit parallel, methods AND classes), which the self-contained style
  already allowed: no `@BeforeAll`, no mutable statics, every file under a `@TempDir`. A new test must keep
  that, and anything competing for a MACHINE-WIDE resource declares it — the two that pick a loopback port
  carry `@ResourceLock("loopback-ports")` + `@Execution(SAME_THREAD)`, because a port freed to be probed is a
  port another thread can take first. Only this suite: `e2eTest` shares branches and tmux sessions between
  rows, and `boardTest` seeds one application's state.
- THE BOARD IS TESTED IN A BROWSER: `./gradlew boardTest` (source set `src/boardTest/java`, NOT in `check`)
  boots the app on a random port and drives the real page in Playwright's own headless Chromium — the page's
  logic (which phases get a column, which buttons a card offers, what a click POSTs, the SSE repaint, the ⌘K
  palette's client-side verdict) runs nowhere else and was hand-checked until 2026-08-17. Three write paths are
  `@MockitoBean`s because a real one would act on the developer's machine: `CommandService`, `TaskLauncher`,
  `NaturalLanguageDispatch`. The browser is Playwright's, never the machine's, so a Mac and a runner drive the
  same build; its shared libraries are in `scripts/linux-test-deps.sh` — the ONE list, not a second one.
  Run it after ANY change to `static/`, and assert through the SERVER (seed `StateService`, stub a command),
  never by evaluating JS in the page.
- E2E matrix: `./gradlew e2eTest` (own source set `src/e2e/java`, NOT in `test`/`check` — it needs git + tmux
  and drives real worktrees, so the fast hermetic gate stays fast). It runs the flow once per `TaskFlowCase`
  with `orchestrator.agent=stub` (`StubAgentRuntime` — the ONE non-deterministic participant replaced; every
  GUI driver is a Mockito double) and asserts an exact end state. Two rules it lives by: widening coverage is
  adding a ROW to `TaskFlowCase.matrix()`, and a combination that is NOT covered is named there with the
  reason — a silent gap reads as coverage. Cleanup kills tmux sessions BY PREFIX, because `tab-per-task`
  creates `<session>-<taskId>` ones the configured name alone would leave behind. It also asserts the SENTENCE
  a flow returns, and `./gradlew test` cannot see it: reword a message and CI is the first thing that notices,
  so run `e2eTest` before pushing one. Row 1 leaves the branch behind when it fails, so rows 2-4 then fail with
  "branch already exists" — fix the FIRST row and re-run before reading the rest as four bugs.
  TWO matrices, on purpose: `TaskFlowCase` × `TaskFlowMatrixTest` is CREATE→TEARDOWN across the viewer
  combinations, and `ReviewRoundCase` × `ReviewAndDeployFlowTest` is everything between — ship, a round, deploy,
  revert, resume — on ONE combination, because a review round does not vary with how terminals are arranged.
  There the verbs go through the board's own HTTP endpoints and the agent reports over `POST /mcp` with its
  worktree header, so origins (`board` vs `mcp`) are asserted end to end and a surface cannot drift from the
  core. Its two doubles are `FakeCodeHost` and `MasterAssistant` — the second is a GUARD rather than a
  stub: nothing in these flows may reach a model any more, so a read that stopped routing through the host
  fails the run instead of paying for it.

## Code quality — the test is the litmus of the production code
- A test is the embodiment of the main code's cleanliness. If a test needs ~5+ objects set up, or its
  cognitive load / composition is high, the SMELL IS IN THE PRODUCTION CODE (poor decomposition /
  isolation), NOT the test — fix the code so the test goes light (sob-ai:unit-testing §5). Never paper
  over it with fatter test setup or shared fixtures.
- NO GOD OBJECTS. THREE collaborators per class is the target, FIVE is the hard ceiling — and that ceiling
  holds for a class that only DELEGATES, because a delegating aggregate is exactly how one grows. Over it,
  GROUP collaborators into a cohesive component (composition, never inheritance) and let callers depend on the
  part they use. The ceiling is not advisory: `MasterShell` sat at eight and its test built the whole screen to
  check a parse, which is how a 31-mock test happens.
- THE TEST IS THE MEASURE, not the line count: a test that needs more than ~3 mocks is telling you the class
  under it does too much. Fix the class, never the fixture (sob-ai:unit-testing §5).
- NO CLASS IS OVER THE CEILING TODAY (checked 2026-08-14: 70 classes, none above five, 47 at three or fewer).
  A new aggregate is how that regresses: when a class would need a sixth collaborator, the answer is a registry
  of small units (see `surface/mcp/McpTools`, and `Move.actions()` for the per-task verbs), never one more field.
- No positional null-soup: config/value records get a builder or `defaults()` + `withX` withers — never a
  10-arg record constructor with a row of `null`s.
- LOMBOK CARRIES THE MECHANICAL BOILERPLATE, and nothing else: `@RequiredArgsConstructor` for injected final
  fields, `@Slf4j` for the logger, `@With` for a record's positional copy-withers (1.18.46 supports `@With` AND
  `@Builder` on records — verified under the Java 25 toolchain; an older note here claimed otherwise). Written
  by hand where the code is not mechanical: a constructor that validates or derives (`OrchestratorPaths`), a
  wither that does more than copy one component (`TaskState.withStatus` stamps history), and
  `TaskState.builder(project, worktree, status)` — Lombok's generated `builder()` cannot demand those three.
- Prefer composition over many injected dependencies; SOLID + clean-code defaults — standard for 30 years,
  apply them, don't reinvent.
- SELF-CONTROL LOOP (mandatory, every code+test change): run the changed tests through the
  sob-ai:unit-testing skill (hand off to an agent). If it reports a test as compositionally heavy / high
  cognitive load / too much setup, that is a signal to REFACTOR THE PRODUCTION CODE until the test is
  light — then re-run. Deliver only when tests are BOTH light and green AND reviewed (next bullet).
- CODE REVIEW IS MANDATORY AFTER EVERY CODE CHANGE, BEFORE COMMITTING, AND IT IS SCOPED TO WHAT THIS SESSION
  TOUCHED — never to "the working diff" and never to the branch. Several sessions work in this tree at once, so
  the tree and the index hold their changes too, and `/code-review` with no target reviews the whole branch since
  it left the remote PLUS everything uncommitted, whoever wrote it. State the scope, and state a LEVEL every time
  (the last one typed is remembered and silently applied to the next call that omits it):
  - before committing: `/code-review medium <the paths you changed>` — the same explicit paths you stage, and for
    the same reason nothing here is staged with `git add -A`.
  - after committing: `/code-review medium <sha>^..<sha>` — a ref range is the only scope another session cannot
    widen while the review runs.
  Stay at `medium` unless the change is genuinely subtle: every level above it fans out eight to ten finder
  subagents plus one verifier per candidate location, and each of them re-reads the changed files and the whole
  of this file. Fix every real finding (or explicitly note why it's a non-issue), then re-review if the fixes are
  non-trivial. No commit lands unreviewed — this is a hard gate, not a suggestion. (A shell hook can only
  *remind*; it cannot invoke a skill, so this is enforced here as a workflow rule, not in settings.json.)

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
  loaded yet (`/status`, `/stats` first, while the board still renders) — diagnosed twice as an
  endpoint bug before the inode was checked. Avoid both by running the staged copy (`./gradlew stageJar`,
  then `build/libs/jagt-run.jar` — a symlink to a per-build `jagt-run-<stamp>.jar`, so re-staging while an
  instance runs cannot touch the inode it holds; a fixed staged name had the same bug and reproduced it once);
  `service/RunningJarWatch` reports it when it happens anyway. (Past sessions
  burned hours chasing this as a logback/preload bug — it is not.)
