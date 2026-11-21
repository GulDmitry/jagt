# Architecture

The map of the code: **what kinds of thing jagt has, and where a new one goes.** The rules live in
[`AGENTS.md`](AGENTS.md), the behaviour in [`openspec/specs/`](openspec/specs/).

Every path below is a package under `dev.jagt.orchestrator`, inside `orchestrator-backend/` — where `gradlew` lives,
**not** the root. So `flow/` is `orchestrator-backend/core/src/main/java/dev/jagt/orchestrator/flow`.
Status is part of the map: *built* means built; fix a stale row, never delete it.

## Contents

- [The kinds](#the-kinds)
- [The rings](#the-rings)
- [The flow machine](#the-flow-machine)
- [The artifact chain](#the-artifact-chain)
- [Ports and who answers them](#ports-and-who-answers-them)
- [The package map](#the-package-map)
- [Where a new thing goes](#where-a-new-thing-goes)
- [The board is two rings too](#the-board-is-two-rings-too)
- [The agent system is a ring](#the-agent-system-is-a-ring)
- [Assembly validation](#assembly-validation)
- [The build](#the-build)

## The kinds

Anything that runs, tells, decides, or is done to a task is one of the **kinds**: one interface — in `port/` when
something outside implements it, in `flow/` when it speaks the table's words — one registry, one folder of
implementations. **If something fits no kind, the kind is missing. Add a kind, never an exception.**

| kind | its one job | state | standard it follows · check pinning it |
|------|-------------|-------|------|
| `task/` | the task record and everything it is made of, its `TaskStatus` too | built — `service/StateService` writes it to `state.json` | ISO/IEC 25010 recoverability: atomic writes, state read across a minor · `StateServiceCompatibilityTest` |
| `flow/` | which status allows what, where each outcome leads, whose turn | built — `TaskChart` (an instance `FlowWiring` composes from the roles, as are `TaskReducer`, `Checkers` and `FlowRules`; regions: lifecycle, stop, plan, report, turn, deploy, session, git, repo, request, checks, approval, infra, acceptance, master, plus derived lazy `ask` and `acceptance beneath a read`), `TaskOffers` (derived lazy regions for the task's facts, and one self-loop edge per offered action), `FlowRules`, `FlowEngine`, `FlowReports`, `Turns`, `Move` | hexagonal centre, one state machine owning every status · `RingsFitness`, `FlowDoorsFitness` |
| `capability/` | one thing that can be done to a task | built — a class per verb | command pattern, one class per verb · `KindsTest` |
| `job/` | work that runs with nobody watching | built — `Job`, `Jobs`, every job and what only jobs use | Spring lifecycle: start work an event or a `Job`, stop work a `SmartLifecycle` · `post-construct-hooks` |
| `notify/` | something a human must be told | built — a point is routed to channels by `jagt.yml` (`Routes`, `Messages`, `ChannelPosts`, `PointWatch`); the contract is `port/channel/Notification` + `port/channel/ChannelDriver` | twelve-factor IV, a channel a backing service chosen in `jagt.yml` · `RoutesTest` |
| `surface/` | who is asking | built — `board`, `mcp`, `agent`, `ui` | OWASP ASVS 5 level 2 · `NotThePageTest`, `LoopbackOrRootTokenTest`; the board's `ui/`: `boardTest`, `node --test` under c8 |
| `command/` | what a human asks that no task owns | built — `GlobalCommand` + `GlobalCommands`, one class per verb | command pattern, one class per verb · `KindsTest` |
| `protocol/` | what crosses into jagt, and what makes it valid | built — every tool, every read, the hooks | ASVS V2 input validation against a JSON Schema · `SchemaTest`; the board's `core/grammar`: `node --test` under c8 |
| check | a rule the code must keep, held for every source set of every project | built — `build-logic`'s conventions apply Checkstyle, Error Prone and NullAway to every source set; a rule test reads `SourceRoots`; main-only only for who in production may act and what a test names as its fixture ([0162](docs/decisions/0162-a-check-holds-for-all-code.md)) | ISO/IEC 25010 maintainability · Checkstyle, Error Prone, NullAway, `cognitive-complexity`, `duplication`; the board's JS, `cd board`: `npm run lint` (Prettier, ESLint, tsc strict, jscpd at 75 tokens), `npm test` (c8), `npm run mutate` (Stryker), in CI's `test` job |
| fitness function | one automated fitness function with a measured number, a threshold and a direction, in one place in the reserved tree | built — `fitness.yml` read by the included build `fitness/`; a coded fitness function a `Fitness` class found by id ([0209](docs/decisions/0209-one-fitness-yml-declares-every-fitness-function-in-a-reserved-tree.md)); its own boundary [0210](docs/decisions/0210-fitness-is-an-included-build-with-its-own-boundary.md) | Building Evolutionary Architectures, ch. 2; ISO/IEC 25010 and 25012 section names · `FitnessTest` |
| `startup/` | why the assembly must not start | built — `port/host/StartupCheck`, `StartupValidation` and every check | fail-fast configuration validation · `StartupValidationTest` |
| deployment example | a file a user copies and edits to run jagt somewhere, in `examples/`; `.dist` stays for a file whose copy is ignored | built — `Dockerfile` and `docker-compose.yml` (jagt's own image), `examples/agent-image.Dockerfile`, `examples/kubernetes.yml`, `examples/server.env`; one walk-through, [`docs/server.md`](docs/server.md), [0171](docs/decisions/0171-the-image-is-a-base-without-agent-clis.md) | twelve-factor app, Kubernetes restricted Pod Security Standard · `KubernetesManifestsTest` |
| `roles/<id>/` | an optional role, off or absent with jagt still whole: nothing outside its modules names or needs it (owner 2026-11-19): a role's words stay in its modules, core holds only the pinned kinds (`RoleModules`); its `design.md` (use cases as WHEN/THEN, who triggers it, its cost against a session doing the same), agreed before its first code, then its definition (`points` the role raises), brief, cases and eval, composed into the task chart | built — `role.yml` → `RoleDefinition` and `CheckerRegion` (its words: [`docs/role-vocabulary.md`](docs/role-vocabulary.md)), the Master's `MasterRegion`; its classes sit in the package set `RoleModules` declares, `RoleNamesFitness` | use cases as WHEN/THEN before code · `RoleNamesFitness` |

**A new kind names its sphere's standard and the check pinning it before its first code.** The gates of testing, QA,
operations and supply chain have one inventory, `openspec/specs/engineering/spec.md`; [`docs/practices.md`](docs/practices.md) copies them.

`service/` is the rest: work kinds share — git, the state file, config, worktrees, agent sessions —
because a class two kinds use belongs to neither; the Master's reading is `service/master/`. So does a rule one
surface asks (`OwnStatusReports`, `HandBackDue`): a surface never decides. `KindsTest` asserts it.
`FitnessTest` ratchets each kind's count to `fitness.yml`
([0176](docs/decisions/0176-every-kind-has-a-ceiling.md)). The board renders
`flow/view/TaskView`; a report is text from the `GlobalCommand` that owns it.

## The rings

Arrows point **inward only**: `adapter/` — the only place an OS is named, and where every vendor lives —
implements the ports, and the use cases (`capability/` `command/` `job/` `notify/` `service/` `surface/`) use the
centre, `port/` `task/` `flow/` `protocol/`: the records, the rules, and the interfaces they declare. `port/` is
*inside* that centre: the rules write those interfaces for their own needs, and `flow/` depends on them.

`RingsFitness` asserts exactly this, on the bytecode through ArchUnit, on the text where bytecode is blind:

- `task/`, `flow/` and `port/` depend on no other ring; `protocol/` only on them; `task/` on neither `flow/` nor
  `port/`, and `port/` not on `flow/`.
- no two top-level folders depend on each other, either way round.
- those four import no Spring; Lombok per [0107](docs/decisions/0107-one-lombok-set-core-included.md).
  Jackson annotations stay: the record **is** the file format.
- no OS name (`osascript`, `notify-send`, `setsid`, an install prefix, a Windows shell), and no vendor or editor
  name anywhere in a source, outside `adapter/`.
- no ring between the centre and the edge depends on `adapter/`.
- no static mutable field in any ring (`static-state`).
- it proves it read every ring, so a refactor cannot empty it.
- it cannot see resources and scripts, or the words a host invents.
- it exempts only `tmuxCommand`, `tmuxSession` and `tmuxSocket`, keys naming a vendor by design; renaming one a human already
  wrote is the owner's call.

## The flow machine

**No capability writes a status.** It does the work and reports an outcome; which status that leads to is the table's
answer. Every type in `flow/`, one question each:

| type | answers |
|------|---------|
| `flow/capability/TaskCapability` | `Outcome run(taskId)` — and names no `TaskStatus` |
| `Outcome` | OK, NOTHING, RELAYED, CONFLICT, PARTIAL, GONE + the sentence, the stamp, the cause |
| `TaskChart` | the regions and their edges: `edge(chart, DEPLOYED, "deploy read red", move, DEPLOY_CONFLICT)` — one per status move |
| `ConflictEnding` | which reads end a deploy conflict, by its cause, and under which stops; `job/conflict/DeployConflictJob` walks it |
| `TaskOffers` | which statuses offer which action: a self-loop per action, guarded by the task's facts |
| `FlowRules` | the questions asked of the chart — `allows`, `allowed`, `next`, `reportable` — and no declaration |
| `Facts` | the guard's second argument — an open request, a live deploy, and a liveness probe passed as a SUPPLIER |
| `FlowEngine` | door one: `rules.allows?` → `capability.run` → `rules.next(outcome)` → ONE status write |
| `FlowReports` | door two: a task's report (`FlowRules.reportable`), or the host's verdict (`read`) |
| `TaskAction` | a verb, named once for every surface |
| `Capabilities` | every `flow/capability/TaskCapability` there is, one per action — the `capability/` kind's registry |
| `Phase` | the step of a task's life a human reads |
| `Pipeline` | what the host last said about the checks, as a verdict rather than as its own wording |
| `task/turn/AgentReport` | what the task's last report means — a question, a permission, no changes, or plain; never parsed from its line |
| `RoundState` | what the last round left behind: that report, plus whether drafted replies are waiting |
| `DashboardLine` | the one contextual line under a card, empty whenever status and next move already answer |
| `Refusal` | a refusal a caller must act on rather than only show |
| `Turns` | who holds a task's turn, from its status, its report and the Master's profile |
| `task/turn/Turn` | that turn on the task: holder, the step that opened it, its digest, since when, each step's last mark |
| `Move` | what can be done to this task, whose turn it is, how loudly it asks |
| `Owner` | whose turn |
| `Attention` | whether the card is an interruption — one value for the badge, the count and the filter |
| `TaskView` | what the board renders, built by `service/task/TaskViews` |

- The lifecycle's legal moves are `flow/TaskChart`, drawn in [`docs/task-chart.md`](docs/task-chart.md);
  the stop's way out, in [`docs/task-stop-chart.md`](docs/task-stop-chart.md); the plan gate,
  in [`docs/task-plan-chart.md`](docs/task-plan-chart.md); the report, in
  [`docs/task-report-chart.md`](docs/task-report-chart.md); the turn, in
  [`docs/task-turn-chart.md`](docs/task-turn-chart.md); the session's signs, in
  [`docs/task-session-chart.md`](docs/task-session-chart.md); the deploy, in
  [`docs/task-deploy-chart.md`](docs/task-deploy-chart.md); the git trust, in
  [`docs/task-git-chart.md`](docs/task-git-chart.md); the repository request, in
  [`docs/task-repo-chart.md`](docs/task-repo-chart.md); the review requests, in
  [`docs/task-request-chart.md`](docs/task-request-chart.md); the checks and the approval a read
  said, in [`docs/task-checks-chart.md`](docs/task-checks-chart.md) and
  [`docs/task-approval-chart.md`](docs/task-approval-chart.md); the infrastructure re-runs, in
  [`docs/task-infra-chart.md`](docs/task-infra-chart.md); acceptance's read, pass and round, in
  [`docs/task-acceptance-chart.md`](docs/task-acceptance-chart.md); what the Master left, in
  [`docs/task-master-chart.md`](docs/task-master-chart.md). What was told once is the ledger `task/turn/ToldOnce`, written by the reducer alone
  ([0144](docs/decisions/0144-what-was-told-once-is-one-ledger-written-by-the-reducer.md)); the other marks are bookkeeping, written by `withMark`.
- `withStatus` has exactly two callers outside the record: the two doors (`FlowDoorsFitness`). A task is born at NEW (`service/TaskProvisioning`).
- **A move with no edge in `TaskChart` is not in the table**; `FlowRules.allows(...)` is the query the engine and the
  projection call, not a declaration.
- **An outcome with no stamp keeps the line the task already carries**, and still transitions if the table names one.
  PARTIAL alone refuses: stamped first, thrown second, so half a change on a shared branch is recorded.
- **An interceptor** (`flow/capability/CapabilityInterceptor`) wraps a verb and may refuse, stopping the work *and* the transition.
- **`Facts.projected`** assumes no live agent and costs nothing; the gate runs the real probe — so a stuck SHIPPING card
  offers SHIP and the gate can still refuse it.

## The artifact chain

A lifecycle is read as artifacts rather than phases: every step ends by writing something the next one reads, and the
chain of them is the audit trail. The framing is [the AI-native SDLC
playbook](https://claude.com/blog/the-ai-native-sdlc-playbook)'s; jagt is its loop from accepted plan to deploy.

| step | what it leaves behind | where |
|------|----------------------|-------|
| `do` | the task as the agent read it, and the rules it works under | `AGENTS.md` in the worktree (`service/worktree/SubAgentBriefing`) |
| a ticket's first read | the ticket as read once, never read again for a round | `ticket-texts/`, beside `state.json` (`service/assistant/TicketTexts`) |
| a plan | what the agent will do, and the probes jagt runs | `plan.md`, at PLAN_PENDING |
| a relay | the one instruction standing right now, and no older one | `task_context.md` |
| the agent works | the diff | the task branch |
| `ship` | the change folded into the specs, commits, a pushed branch, a request per repository | the branch, `reviewRequests` |
| a round | what the agent means to answer, before anything is posted | `review_replies.md` |
| any report | the sentence, its stamp, whose move it is | `state.json` |
| `deploy` | the merge commit `revert` walks back | the deploy branch, and the task's `deployment` |
| `done` | the documents and `finished.json`, copied out before the worktree goes | `archive/<yyMMdd-HHmmss>-<taskId>/`, beside `state.json` |

- **Only the middle is in git**: branch, commits and request outlive the task; the documents are copied out
  first (`capability/done/FinishedArtifacts`) and never read back, so changing their format migrates nothing.
- **The chain is readable while the task lives** — `code <alias>` opens the worktree holding all of it.
- **The front of the chain can be your own words**: with no ticket, what you typed is the task, names its branch,
  and lands in `task_context.md`.

## Ports and who answers them

| port | implemented by |
|------|----------------|
| `TaskStore` | `service/StateService` |
| `AgentPresence` | `service/AgentSessions` |
| `flow/capability/TaskCapability` | one per verb under `capability/` |
| `flow/capability/CapabilityInterceptor` | `capability/ship/SpecFold`, `capability/stop/DeployLiftsStop`; an install may add its own step |
| `Specs` | `adapter/worktree/OpenSpec` |
| `AgentRuntime` | `adapter/agent/{Claude,Stub}AgentRuntime` (+ `AbstractAgentRuntime`), `adapter/agent/{codex/Codex,opencode/OpenCode}AgentRuntime`, `adapter/replay/ReplayAgentRuntime` |
| `{Tracker,Routing,CodeHost,Command}Assistant`, `McpHealth` | `adapter/assistant/HeadlessClaude<port>`, `reach/ClaudeMcpHealthProbe`; `Replay<port>` for tracker and code host |
| `CodeHost` | `adapter/codehost/ChosenCodeHost` choosing a `CodeHostDriver` (`GitHubCli`, `GitLabCli`, `CommandPipeline`, `NoCodeHost`, `ReplayCodeHost`) by `codeHost`; re-running a pipeline, closing a request and opening the acceptance workspace's request are jagt's code-host writes |
| `CodeHostCommands` | `adapter/codehost/cli/{GitHub,GitLab}Commands` — each code-host CLI's word, read verbs and command groups; `service/gate/ToolGate` judges every one wherever it stands |
| `RoundReviewer` | `adapter/assistant/ChosenRoundReviewer` choosing a driver by config (`HeadlessClaudeRoundReviewer`, OpenCode), `ReplayRoundReviewer` (a stand recording played back) |
| `CheckerReader` | `adapter/assistant/ChosenCheckerReader` choosing a driver by config (`HeadlessClaudeCheckerReader`, `OpenCodeCheckerReader`), `ReplayCheckerReader` |
| `EnvironmentProbe` | `adapter/assistant/HttpEnvironmentProbe` |
| `FlowRunner` | `adapter/flows/FlowsRunner` choosing `PlaywrightRunner` by `flows.driver` in the role folder, read per run — scripted flows run as argv, judged from the JSON report |
| `TrackerWorkflow` | `adapter/tracker/JiraWorkflow`, `NoTrackerWorkflow` |
| `Reach` | `service/reach/ReachLedger` — jagt's lost reach per driver and host, held in memory, written by the drivers, read by the board's head and `login`; `adapter/assistant/AssistantBreaker` writes the assistant's outage into it |
| `ChannelDriver` | `adapter/channel/{DesktopNotifier,CommandChannel,TeamsWorkflowChannel}` — one per channel `kind`; `notify/Notifications` routes a point to the channels `jagt.yml` lists |
| `Processes` | `adapter/ProcessRunner` |
| `Host` | `adapter/SystemHost` — OS name, home, environment, executables on PATH, the running jar |
| `SessionHost` | `adapter/tmux/TmuxSessionHost` — the one single-impl seam; `openCommandWindow` runs a command as a window's process |
| `ReadScope` | `adapter/tmux/TmuxReads` — a job run lists the windows and processes once for every task it visits ([0147](docs/decisions/0147-the-tick-scope-is-its-own-port.md)) |
| `SessionLog` | `adapter/agent/log/ClaudeSessionLog` — what a session's own log says it last did |
| `SubscriptionUsage` | `adapter/agent/{Claude,Codex}SubscriptionUsage` |
| `BuildTools` | `adapter/worktree/RepositoryBuildTools` |
| `LineDiff` | `adapter/worktree/DiffUtilsLineDiff` |
| `RequestlessShip` | `service/repo/HostlessShips` |
| `ToolGateEnvelope` | `adapter/agent/hook/ClaudeToolGateEnvelope` |
| `WorktreeProcesses` | `adapter/worktree/LsofWorktreeProcesses` |
| `WorktreeContainers` | `adapter/worktree/{DockerComposeContainers,NoWorktreeContainers}` — by `orchestrator.containers` |
| `UserNotifier` | `adapter/macos/MacNotifier`, `adapter/linux/LibNotifyNotifier`, `adapter/channel/NoNotifier` |
| `KeepAwake` | `adapter/macos/Caffeinate`, `adapter/linux/SystemdInhibit`; none: no bean |
| `TerminalDriver` | `adapter/terminal/AbstractKittyTerminalDriver` (+ one per platform), `NoTerminalDriver` |
| `EditorDriver` | `adapter/terminal/{CliEditorDriver,NoEditorDriver}` |
| `StartupCheck` | `startup/` and the edge — see [Assembly validation](#assembly-validation) |
| `AgentWorktree`, `Notification`, `Answer`, `Sandbox` | nothing: value **records** the ports pass |

- **Every third party jagt talks to is a row here**, its driver chosen by a `jagt.yml` key with a default; a vendor
  guessed from a name, a URL or a host is a defect ([0130](docs/decisions/0130-every-third-party-is-a-configured-seam.md)).
- **A driver that cannot reach its party fails jagt, not a task**: the board's head carries it, never a card's stop.

## The package map

Every package of every module as it stands on disk, one line each; `PackagesTest` fails on a package missing here, on a
row with no package, on a package of more than ten classes and on one of a single class
([0166](docs/decisions/0166-packages-are-architecture.md)).

| package | module | holds |
|---------|--------|-------|
| `flow` | core | the chart, reducer, rules and engine: which status allows what, whose turn |
| `flow/capability` | core | a verb's contract: `TaskAction`, `TaskCapability`, interceptors, outcomes |
| `flow/deploy` | core | the chart regions for deploy: phases, steps, pipeline, red causes |
| `flow/repo` | core | the chart regions for git, repository and request |
| `flow/role` | core | the chart's roles and points: arbiter, checker role, master phase |
| `flow/session` | core | the chart's session facts: stop kinds and holds, standing, human waits |
| `flow/view` | core | what a reader sees of a task: `TaskView`, attention, owner, phase |
| `port` | core | the interfaces the rules declare that no kind owns: store, events, presence, tracker workflow |
| `port/agent` | core | the agent runtime, its sandbox, session log, usage and tool-gate ports |
| `port/assistant` | core | the headless assistants and readers, their answers |
| `port/channel` | core | the channel driver port and what it carries |
| `port/codehost` | core | the code host port and its commands |
| `port/host` | core | the host: processes, keep-awake, supervisor, startup checks, the data layout, the release feed |
| `port/reach` | core | what jagt's reach loss is told in |
| `port/terminal` | core | terminal, editor and session-host ports |
| `port/worktree` | core | worktree-facing ports: flow runner, build tools, line diff, specs, containers, processes |
| `protocol` | core | what crosses into jagt: tool messages, reads, hooks, schema |
| `protocol/debug` | core | the read-only debug reads' messages |
| `protocol/message` | core | the tool messages a session sends and their arguments |
| `protocol/plan` | core | the plan's parts: criteria, probes, repositories, judgements |
| `protocol/read` | core | the reads jagt asks a model for: round, review, ticket, retry policy |
| `task` | core | the task record, its status and the migrations of old records |
| `task/acceptance` | core | what the acceptance role leaves on a task: verdict, decline, question, ending |
| `task/deploy` | core | the deployment record, its conflicts, failed jobs and infra retries |
| `task/launch` | core | what a task is born from: request, name, routing question, tracker facts |
| `task/master` | core | what the Master leaves on a task: mode, rights, the record |
| `task/plan` | core | the plan gate, its artifacts and probes |
| `task/repo` | core | a task's repositories: remotes, branch strategy, trust, project config |
| `task/review` | core | review rounds, loop breaker and the merge-request facts |
| `task/session` | core | the session record, its wake and the ask stamp |
| `task/stop` | core | a stop on a task, its guard and triage |
| `task/text` | core | text and file helpers of the record: ANSI, atomic files, UTC time |
| `task/turn` | core | the turn on a task: steps, reports, human waits, what was told once |
| `task/usage` | core | spend of a task: tokens, model tier, cost per accepted task |
| `capability` | usecase | the verbs with no feature of their own: plan, scenarios, sweep, trust, git state guard |
| `capability/deploy` | usecase | deploy and revert: capability, runner, service |
| `capability/done` | usecase | done: the capability, retirement and finished artifacts |
| `capability/code` | usecase | open a task in the editor and its diff |
| `capability/repo` | usecase | attach and remove a repository |
| `capability/session` | usecase | focus, hand off, respawn and run a session |
| `capability/ship` | usecase | ship: capability, service, spec fold and merged guard |
| `capability/stop` | usecase | pause, lift and escalate a stop |
| `command` | usecase | what a human asks that no task owns, with no feature of its own: `GlobalCommand`, do, resume, help |
| `command/activity` | usecase | the activity report |
| `command/host` | usecase | commands about jagt itself: jobs, log, login, restart, run, supervise |
| `command/master` | usecase | the Master's commands and records |
| `command/stats` | usecase | stats and finished reports with their renderers |
| `command/workspace` | usecase | projects and worktrees as commands |
| `config` | usecase | the properties jagt reads from `jagt.yml` |
| `config/source` | usecase | `jagt.yml` read and resolved: `${VAR}`, `${file:}`, `JAGT__` overrides, the environment and files it reads |
| `config/home` | usecase | the install home of a jar run from `versions/<v>/`, and the files the jar carries into it |
| `config/role` | usecase | a role's definition, files, options, cases and session brief |
| `job` | usecase | unattended work with no feature of its own: `Job`, `Jobs`, the scheduler, the worktree sweep |
| `job/checker` | usecase | one checker role's jobs: check, decline, wait, records and prompts |
| `job/checker/wiring` | usecase | the set of those jobs per installed checker, and what they share |
| `job/checker/workspace` | usecase | a checker's workspace offered as a request after a check, once the role turns it on |
| `job/conflict` | usecase | deploy-conflict jobs and the reads that end them |
| `job/deploy` | usecase | deploy checks, pipelines and infra retries |
| `job/host` | usecase | jobs about the install: keep-awake, the running-jar watch and the newer-release check |
| `job/master` | usecase | the Master's deploy, repo, ship and stop jobs |
| `job/review` | usecase | auto-review and the verify and your-move jobs |
| `job/session` | usecase | session liveness, wake, restart and watchdog jobs |
| `job/stop` | usecase | stop reads |
| `job/triage` | usecase | the triage role's job, panel and failure |
| `job/usage` | usecase | usage-limit alerts and the job reading them |
| `job/tracker` | usecase | tracker intake and ticket close jobs |
| `job/verdict` | usecase | the Master's verdict and answer jobs |
| `notify` | usecase | points routed to channels: routes, messages, posts, watch |
| `service` | usecase | state, config, commands and the task flow's shared work that no feature owns |
| `service/assistant` | usecase | assistant reads and their charged, kept and paid forms |
| `service/deploy` | usecase | deploy targets and infra retries |
| `service/gate` | usecase | the tool gate: read lines, scopes and the verdict on a command |
| `service/git` | usecase | git commands, states, worktrees, deploys and diffs |
| `service/launch` | usecase | starting a task: routing, placement, resume |
| `service/log` | usecase | jagt's own logs: rolling files, press, stop and miss logs |
| `service/master` | usecase | what the Master reads: panels, briefs, decisions, brake |
| `service/plan` | usecase | plan approval, baseline, placement and probes |
| `service/presence` | usecase | session presence: ledger, probe, wakes, keep-alive |
| `service/project` | usecase | projects: cards, folders, setup, removal; the basic settings and the line-wise writer of `jagt.yml` |
| `service/reach` | usecase | the reach ledger and what writes it |
| `service/release` | usecase | the kept newer release and the switch of `current` to it |
| `service/repo` | usecase | repositories: remotes, departures, hostless ships, merged requests |
| `service/report` | usecase | status reports and their effects |
| `service/review` | usecase | review rounds: cadence, drafts, sweep |
| `service/role` | usecase | installed roles, their beans, settings and config origins |
| `service/scenario` | usecase | scenario sessions and windows |
| `service/session` | usecase | session hosting, lines, prompts and offered models |
| `service/stop` | usecase | stops: announcements, policy, per-task stops |
| `service/task` | usecase | task views, facts, artifacts and finished tasks |
| `service/turn` | usecase | turns: relay, tree, human wait recording |
| `service/usage` | usecase | usage ledger, limits and wakes |
| `service/worktree` | usecase | worktree files, changes, bases and briefing |
| `startup` | usecase | why the assembly must not start: validation and its checks |
| `startup/role` | usecase | the role checks of startup |
| `adapter` | adapter | OS-free process and host plumbing: runner, executables, host, platform and toolchain checks |
| `adapter/agent` | adapter | the agent drivers' shared and Claude-specific parts |
| `adapter/agent/codex` | adapter | the Codex driver |
| `adapter/agent/hook` | adapter | Claude's hook JSON and tool-gate envelope |
| `adapter/agent/log` | adapter | session logs and prices per agent |
| `adapter/agent/opencode` | adapter | the OpenCode driver |
| `adapter/agent/trust` | adapter | Claude's config directory and trust file, exposed to `stand` |
| `adapter/assistant` | adapter | the headless assistants, readers and replays |
| `adapter/assistant/reach` | adapter | MCP and local-model reach probes |
| `adapter/channel` | adapter | notification channel drivers |
| `adapter/codehost` | adapter | code host drivers: choice, pipeline, replay, none |
| `adapter/codehost/cli` | adapter | the GitHub and GitLab CLIs and their commands |
| `adapter/flows` | adapter | the scripted-flows runner and its Playwright driver |
| `adapter/linux` | adapter | Linux notifier, kitty driver and keep-awake |
| `adapter/macos` | adapter | macOS notifier, terminal drivers, keep-awake and supervisor |
| `adapter/replay` | adapter | a stand recording played back: runtime, sessions, moves, reads |
| `adapter/process` | adapter | the one owner of child processes: spawn, end each tree, reap; exposed to `stand` |
| `adapter/release` | adapter | the release feed drivers: GitHub releases, none |
| `adapter/terminal` | adapter | the kitty base, editor driver and kitty options |
| `adapter/values` | adapter | `${file:}` and `JAGT__` for the keys Spring binds |
| `adapter/tmux` | adapter | the tmux session host and its reads |
| `adapter/tracker` | adapter | tracker drivers: file and Jira workflows |
| `adapter/worktree` | adapter | worktree-facing drivers: line diff, containers, processes, build tools, OpenSpec |
| `surface/agent` | surface | what a session calls into: hooks, prompts, gates, reports |
| `surface/board` | surface | the board's HTTP: API, projects, interactions, event stream |
| `surface/board/auth` | surface | the board's login: the `auth` seam's properties, start check, security chains and CSP |
| `surface/board/auth/attempts` | surface | the board's login attempts: per-peer back-off, the login audit line, the password outcome events |
| `surface/board/files` | surface | what the board is served from: the jar's copy or `board.dir`, the user's i18n overlay, the manifest MIME |
| `surface/board/filter` | surface | what stands in front of the board's requests: the chains for refused requests and the agent door, the strict firewall, origin, aborted connections |
| `surface/board/terminal` | surface | the Session tab's live terminal: the WebSocket a window's output goes out on and a human's keys come in |
| `surface/board/task` | surface | per-task board endpoints and the palette's tiers |
| `surface/mcp` | surface | the MCP endpoint: controller, protocol service, registry, handlers |
| `surface/mcp/refusal` | surface | how a tool refuses and who may call: caller scope, root token, failure, refusal |
| `surface/mcp/tools` | surface | the MCP tool groups, each declaring its own scope |
| `surface/ui` | surface | the console banner, failure page and startup failure |
| `surface/ui/log` | surface | log archives and rolling, the port guard, task alias feed and filter |
| `stand` | stand | the Blood Games harness: runs, scripts, engines, host stubs, evidence |
| `stand/invariant` | stand | what a finished run must never show: the checks over its records |
| `stand/mutation` | stand | the mutation book and its rewrites of a task |
| `stand/replay` | stand | a run's recording and the snapshots it replays from |
| `(root)` | backend | the application, its wiring and the one isolation of state writes |

### Over ten classes

A package-private partner that cannot move without widening keeps its package whole, until its partners move; never by raising the number.

| package | why |
|---------|-----|
| `flow` | package-private chart types (`Fact`, `Landing`, `TaskMove`, `Phases`, `Role`) are partners of `TaskChart` and `TaskReducer` |
| `protocol` | package-private `Wire` is the codec of eight message and read records beside it |
| `service` | package-private `RoundBrief`, `TaskNotes` and `WorktreeSandbox` tie `HandBack`, `RoundRelay`, `AgentSessions` and their readers |
| `service/master` | package-private `RoundQuotes` and `RoundFacts` are partners of `MasterPanel` |
| `service/git` | twelve classes of one feature, no sub-kind of three; `GitStates` is package-private to `GitWorktrees` |
| `adapter/agent` | package-private `ClaudeTranscripts` ties the Claude classes together |
| `stand` | one harness, thirty-nine classes: runs, engines, host stubs and evidence; its split is open |
| `stand/invariant` | seventeen checks of one feature, no sub-kind of three; `StateFiles` is package-private to them |
| `adapter/assistant` | package-private `HeadlessClaude` and `HeadlessOpenCode` tie the assistants and readers built on them |

### A single class

None today; a package of one class folds into its parent.

## Where a new thing goes

| you want to | do this |
|-------------|---------|
| add a per-task verb | a constant in `flow/capability/TaskAction`, a class in `capability/`, **and** its offer in `flow/TaskOffers` with the edge in `flow/TaskChart` leading each outcome |
| add a report | one `GlobalCommand` in `command/` with `report()` true — the board picks it up |
| add a launch shortcut | a `GlobalCommand` whose `part()` names the board form it opens |
| replace a built-in verb | another `TaskCapability` for the same action with a higher `priority()` |
| run something before/after a verb | a `flow/capability/CapabilityInterceptor` for that action — never a new status |
| add unattended work | one `Job`; `Jobs` schedules it and the `jobs` report lists it |
| notify somewhere else | one `ChannelDriver` in `adapter/` and a channel of its `kind` in `jagt.yml`; no caller changes |
| support another host, tracker, agent or OS | one adapter behind its port, plus the `@ConditionalOnProperty` value |

- A new verb reaches the board generated from `flow/capability/TaskAction`: `Move.actions()` (legal only), an illegal one refused by `FlowEngine`.
- **The surface holds no list** — the usual way to break that is building a button in `board/components/Card.js`.
- An action the table never mentions is offered by nobody, so `startup/FlowCheck` refuses to start.
- **The tree shows layers, then features, then kinds**: a new kind of thing is named in [the package map](#the-package-map)
  before its first class; three classes of one kind or feature get their own sub-package (`capability/ship/`); nothing is
  split ahead of that, and no package holds a single class or more than ten
  ([0166](docs/decisions/0166-packages-are-architecture.md)).
- `do` and `resume` are named literally only in tier 2: `PaletteContext`'s prompt maps a line onto them,
  `NaturalLanguageDispatch` runs them.

## The board is two rings too

`board/` is HTML/CSS/JS, an app of its own that the jar carries a copy of, no build step and no external asset
([`docs/rules/design.md`](docs/rules/design.md)). Native ES modules in three layers: `core/` holds the page's data and
calls without a node, `components/` draws from props alone, `ui/` owns the events, the dialogs and the one render;
`app.js` only wires them. The backend and the board meet only in `contracts/`
([0170](docs/decisions/0170-the-board-is-its-own-app-behind-one-contract.md)). A component is Preact written with `htm`,
both in `vendor/` behind the import map ([0046](docs/decisions/0046-the-board-renders-preact-components-with-no-build.md)).

| file | the question it answers |
|------|------------------------|
| `core/api`, `core/store`, `core/format`, `core/dom` | how the backend is called; what the page knows; the two clocks; a safe link |
| `core/grammar` | what a typed line means, before anything runs |
| `components/html`, `vendor/` | how a component is written; the libraries, pinned by `BoardAssetsTest` |
| `components/TaskList` | every task as a row, the human's own in a band on top |
| `components/Pane`, `components/Card`, `components/Meta` | the open task: its card, its verbs, its tabs |
| `components/JobsTable`, `components/ProjectChecks`, `components/Report` | the jobs by whom they serve; what reading a path found; a report's text, one renderer for the dialog and the round tab |
| `ui/render` | one repaint from the snapshot, and the one delegated click and submit on the board |
| `ui/selection`, `ui/tabs`, `ui/seen`, `ui/flags` | which task is open, which tab and what it read, what changed while closed, which tasks are flagged |
| `ui/header`, `ui/glance`, `ui/menu` | the chips above the board, the tab's title and icon, the Reports menu |
| `ui/launch`, `ui/projects`, `ui/addproject`, `ui/submit` | the New task and Add project dialogs, and their one submit path |
| `ui/jobs` | the jobs table inside the Jobs report, and its Run now |
| `ui/filters`, `ui/order` | what narrows and what re-orders |
| `ui/act`, `ui/inflight` | running one action: what is in flight, what it asks first |
| `ui/palette`, `ui/keys` | ⌘K's two tiers, and every key binding in one table |
| `ui/dialogs`, `ui/toast`, `ui/tips` | what opens over the board, what is said in passing, what a hover shows |
| `ui/legend`, `ui/legend/` | what every mark means, as the page's own elements — their one definition; `card` and `page` hold the rows |
| `ui/refresh`, `ui/interactions` | how the page learns a task changed; what the human pressed |
| `styleguide.html`, `styleguide/` | every component in every state, from fixtures; `boardTest` loads it |

- **A component reads no module and calls nothing**: its facts arrive as props, its presses leave as `data-*`.
- **A module owns its own nodes** — never `getElementById` for another module's; one needing another to act takes a
  callback at wiring time (`palette.wire`, `header.onBarClick`, `toast.showLog`).
- **Rendering is pure and total.** A component is a function of its props, the grid is one keyed render, and no card
  carries a closure — a button carries `data-action`/`data-select` and `ui/render` delegates the one listener.
- **A new component gets its states in `styleguide/`** before the board uses it.
- **One answer per question.** One transport, one toast, one submit pipeline, one key table.
- **A board file over ~150 lines is the signal to split**, not to add a section comment.

## The agent system is a ring

The coding agent is an adapter; the inner rings know one port — launch a session in a worktree, provision it, name the
file it reads its instructions from.

- **No shared instruction file is named after one vendor.** The system-knowledge file is `AGENTS.md`
  (`port/agent/AgentRuntime.SYSTEM_KNOWLEDGE_FILE`), in a provisioned worktree and in this root, where `CLAUDE.md` links to it
  — never two copies.
- **How an agent reaches the MCP server is the adapter's business** — direct HTTP with a working-directory header, or a
  stdio bridge for a CLI that can only spawn a server.
- **What a session is doing is read, never asked**: the log a CLI keeps answers liveness (`lastSessionActivity`) and its
  own hooks report a stop (`surface/agent`) — no model in either path, so a session out of tokens still reaches the
  board. Which events a CLI reports is a resource (`adapter/…/resources/hooks/`), not Java.
- **What a session may push is refused below the CLI as well**: `service/git/WorktreeHooks` writes a `pre-push` into the
  worktree's own `.jagt/hooks` and the launch command points that session's git at it, so a CLI with no hooks of its own
  meets it too. Nothing goes into the repository ([`git`](openspec/specs/git/spec.md)).
- **The one-shot assistant is a separate port**: an install may run a local model for text-to-command and a vendor CLI
  for the sessions. It is the only place jagt spends money, so it is metered.
- **Cloud and local models alike** ([0128](docs/decisions/0128-cloud-and-local-models-alike.md)): the model behind a
  session is the CLI's own setting; a feature built on a cloud trait (cache, limits, spend) works without it.

## Assembly validation

At startup, **one report of everything wrong**, never first-failure, because a half-valid assembly boots and then does
nothing: `port/host/StartupCheck` implementations are collected by `startup/StartupValidation`, which throws
`Misconfigured` once with every problem. Some live in `startup/`, the rest at the edge, which can name the
key that fixes it. The composition half, and what of it exists:

| # | check | state |
|---|-------|-------|
| 1 | every flow rule names a registered capability | run time only, and as an `IllegalStateException` — a 500, not a sentence |
| 2 | every status something can put a task into | built (`startup/FlowCheck`) |
| 3 | every (status × capability × outcome) decided | planned — needs a capability to declare its outcomes |
| 4 | a job's declared capability and watched statuses exist | partly — a `TaskJob` declares its `Watch` and `Jobs` hands it only those tasks (`JobsTest`); capability still planned |
| 5 | a required port capability present in the selected adapter | weak form: a configured type resolves to a bean, and the platform it was picked for is the one jagt runs on (`adapter/PlatformCheck`) |
| 6 | one owner per id | built for all three registries |
| 7 | every role under `roles/<id>/` compiles and composes into the chart | built (`service/role/InstalledRoles`, `startup/role/RoleCheck`) — a role that does not is skipped with its reason, a note and never a problem |

- No "stuck status" check beside #2, deliberately: the report door judges the status being reported, not the one being
  left, so no status can trap a task.
- #5 checks nothing about thread resolution, tab titles or an attachable session host.
- #6 refuses to start on a duplicate or blank job id, a duplicate command verb or an equal capability priority.
- **The refusing half exists; the degrading half is the roles alone** — a missing kitty refuses rather than falling back.
  A role refused by its lint is left out and the rest start (`StartupCheck.notes`).

## The build

Eight Gradle modules under `orchestrator-backend/`, the compiler holding the dependency rule; `module-isolation` pins a module's inside (0211).

| module | holds | sees |
|--------|-------|------|
| `:statechart` | `dev.jagt.statechart`: a Harel/SCXML statechart engine, a pure macrostep over regions and a data model | the JDK and JSpecify only, no jagt type |
| `:core` | `task/` `flow/` `port/` `protocol/` | nothing of jagt's, no Spring |
| `:usecase` | `capability/` `command/` `job/` `notify/` `service/` `config/` `startup/` | `:core` |
| `:adapter` | `adapter/` | `:core`, `:usecase` |
| `:surface` | `surface/` | `:core`, `:usecase` |
| `:stand` | `stand/`: the Blood Games harness, built apart; nothing consumes it and the jar never ships it | `:core`, `:usecase`, `:adapter` |
| `:mutation-scope` | `dev.jagt.mutationscope`: the pitest filter that keeps `pitestDiff`'s mutants on changed lines, loaded by pitest's tool classpath ([0196](docs/decisions/0196-pitestdiff-mutates-only-the-changed-lines.md)) | pitest only |
| root | `OrchestratorApplication`, `FlowWiring`, `RuntimeWiring`, `application.yml` | all four — the assembly |

- Tests live with what they test: `:core:test` (no container at all), `:usecase:test`, `:adapter:test` (real git,
  `lsof`, binaries), `:surface:test`, `:stand:test` (the stand harness's own, outside the assembly), and the root's own few.
- A `:usecase` class only a real binary tests honestly (the git components, `WorktreeHooksTest`) is tested in `:adapter:test`.
- Six suites stay out of `check`: `e2eTest` (git + tmux, real worktrees), `standTest` (docker, kind),
  `boardTest` (a real browser), `linuxDriverTest` (Linux binaries, container), `promptEval` and `roleEval` (the assistant's CLI, tokens).
- Spotless formats every source set with google-java-format before compile; `spotlessCheck` is part of `check`.
- `build-logic/` and `fitness/` are included builds: one plugin per concern, never build logic in a build file.
- `./gradlew test e2eTest boardTest` runs everything runnable here; `./gradlew build stageJar && java -jar build/libs/jagt-run.jar` starts the board.
- **A bean has one constructor**, the one a test calls too: what varies — a clock, a pool, a policy, a host fact — is
  a collaborator that `RuntimeWiring` or `adapter/SystemHost` supplies (`constructors-called-from-main`).
- **No init hook that does work**: `@PostConstruct` validates configuration only; start work is an event or a `Job` (`post-construct-hooks`).
- `config/` keeps what the human wrote and the edge resolves a binary where it spawns it, so no use case names `adapter/Executables`.
