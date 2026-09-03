# Architecture

The map of the code: **what kinds of thing jagt has, and where a new one goes.** The rules live in
[`AGENTS.md`](AGENTS.md), the situations in [`USE-CASES.md`](USE-CASES.md).

Every path below is a package under `dev.jagt.orchestrator`, inside `orchestrator-backend/` — where `gradlew` lives too,
**not** the repository root. So `flow/` is `orchestrator-backend/core/src/main/java/dev/jagt/orchestrator/flow`.

Status is part of the map: a row saying *built* means built, checked on 2026-09-01; fix a stale row, never delete it.

## The kinds

Anything that runs, tells, decides, or is done to a task is one of the **kinds**: one interface — in `port/` when
something outside implements it — one registry, one folder of implementations. **If something fits no kind, the kind is
missing. Add a kind, never an exception.**

| kind | its one job | state |
|------|-------------|-------|
| `task/` | the task record and everything it is made of | built — `service/StateService` writes it to `state.json` |
| `flow/` | which status allows what, and where each outcome leads | built — `FlowRules`, `FlowEngine`, `FlowReports`, `Move` |
| `capability/` | one thing that can be done to a task | built — a class per verb |
| `job/` | work that runs with nobody watching | built — `Job` + `Jobs`; the five impls still live in `service/` |
| `notify/` | something a human must be told | built — the fan-out; the contract is `port/Notification` + `port/Notifier` |
| `surface/` | who is asking | built — `board`, `mcp`, `agent`, `ui` |
| `command/` | what a human asks that no task owns | built — `GlobalCommand` + `GlobalCommands`, one class per verb |

`service/` is the rest: work more than one kind shares — git, the state file, config, worktrees, agent sessions —
because a class two kinds use belongs to neither. The board renders `flow/TaskView`; a report is text from the
`GlobalCommand` that owns it.

## The rings

Arrows point **inward only**: `adapter/` — the only place an OS is named, and where every vendor lives —
implements the ports, and the use cases (`capability/` `command/` `job/` `notify/` `service/` `surface/`) use the
centre, `port/` `task/` `flow/`: the records, the rules, and the interfaces they declare. `port/` is *inside*
that centre rather than a ring around it — those interfaces are written by the rules for their own needs, and
`flow/` depends on them.

`RingsTest` asserts exactly this, and nothing more:

- `task/`, `flow/` and `port/` name no other ring — as an import, a static import, or a qualified name.
- those three import no Spring and no Lombok. Jackson annotations stay: the record **is** the file format.
- no OS name (`osascript`, `notify-send`, `setsid`, an install prefix, a Windows shell) outside `adapter/`.
- no ring between the centre and the edge names `adapter/` — a use case naming the edge is the rule backwards.
- it proves it read every ring: a root that stops matching after a refactor goes green guarding nothing.
- it cannot see resources and scripts, the words a host invents, or a cycle between two *sibling* folders.
- the `platform`, `viewer`, agent-binary and `kitty*` keys name vendors by design, each read by one adapter, and
  renaming one a human has already written is the owner's call.

## The flow machine

**No capability writes a status.** It does the work and reports an outcome; which status that leads to is the table's
answer. Every type in `flow/`, one question each:

| type | answers |
|------|---------|
| `port/TaskCapability` | `Outcome run(taskId)` — and names no `TaskStatus` |
| `Outcome` | OK, NOTHING, RELAYED, CONFLICT, PARTIAL, GONE + the sentence, the stamp, the cause |
| `FlowRules` | `rule(DEPLOY).from(<statuses>).when(<guard>).on(OK, DEPLOYED).on(CONFLICT, DEPLOY_CONFLICT).add()` |
| `Facts` | the guard's second argument — an open request, and a liveness probe passed as a SUPPLIER |
| `FlowEngine` | door one: `rules.allows?` → `capability.run` → `rules.next(outcome)` → ONE status write |
| `FlowReports` | door two: a status the task itself reports, gated by `FlowRules.reportable` |
| `Capabilities` | every `port/TaskCapability` there is, one per action — the `capability/` kind's registry |
| `Phase` | the step of a task's life a human reads, since four statuses all read as "review" |
| `Pipeline` | what the host last said about the checks, as a verdict rather than as its own wording |
| `AgentReport` | what the agent is reporting about a round — a question, no changes, or progress |
| `RoundState` | what the last round left behind: that report, plus whether drafted replies are waiting |
| `DashboardLine` | the one contextual line under a card, empty whenever status and next move already answer |
| `Refusal` | a refusal a caller must act on rather than only show |
| `Move` | what can be done to this task, whose turn it is, how loudly it asks |
| `Owner` | whose turn |
| `Attention` | whether the card is an interruption — one value for the badge, the count and the filter |
| `TaskView` | what the board renders, built by `service/TaskViews` |

- `withStatus` has exactly two callers outside the record itself: the two doors. Two honest exceptions — a task is born
  at NEW (`service/TaskProvisioning`), and `capability/deploy/DeployService` *reads* one to know it is resuming
  (DEPLOY_CONFLICT).
- **A declaration without `.add()` is not in the table**; `FlowRules.allows(...)` is the query the engine and the
  projection call, not a declaration.
- **An outcome with no stamp keeps the line the task already carries**, and still transitions if the table names one.
  PARTIAL alone refuses: stamped first, thrown second, so half a change on a shared branch is recorded.
- **An interceptor** (`port/CapabilityInterceptor`) wraps a verb and may refuse, stopping the work *and* the transition.
- **`Facts` passes liveness as a supplier**: a card uses `Facts.projected` (assume not live, costs nothing), the gate
  the real probe — which is why a stuck SHIPPING card offers SHIP and the gate can still refuse it.

## The artifact chain

A lifecycle is read as artifacts rather than phases: every step ends by writing something the next one reads, and the
chain of them is the audit trail. The framing is [the AI-native SDLC
playbook](https://claude.com/blog/the-ai-native-sdlc-playbook)'s; jagt is its loop from accepted plan to deploy.

| step | what it leaves behind | where |
|------|----------------------|-------|
| `do` | the task as the agent read it, and the rules it works under | `AGENTS.md` in the worktree (`service/SubAgentBriefing`) |
| a relay | the one instruction standing right now, and no older one | `task_context.md` |
| the agent works | the diff | the task branch |
| `ship` | commits, a pushed branch, one review request per repository | the branch, and `reviewRequests` on the task |
| a round | what the agent means to answer, before anything is posted | `review_replies.md` |
| any report | the sentence, its stamp, whose move it is | `state.json` |
| `deploy` | the merge commit `revert` walks back | the deploy branch, and `deployCommit` on the task |
| `done` | nothing: the worktree goes, and everything jagt wrote into it with it | — |

- **Only the middle is in git**: branch, commits and request outlive the task, while the briefing, the standing
  instruction and the drafted replies go with the worktree `done` removes.
- **The chain is readable while the task lives** — `ide <alias>` opens the worktree holding all of it at once.
- **The front of the chain can be your own words**: with no ticket, what you typed is the task, names its branch,
  and lands in `task_context.md`.

## Ports and who answers them

| port | implemented by |
|------|----------------|
| `TaskStore` | `service/StateService` |
| `AgentPresence` | `service/AgentSessions` |
| `TaskCapability` | one per verb under `capability/` |
| `CapabilityInterceptor` | nothing yet — the seam is there for an install to add its own step |
| `AgentRuntime` | `adapter/agent/{Claude,Codex,Stub}AgentRuntime` (+ `AbstractAgentRuntime`) |
| `MasterAssistant` | `adapter/assistant/HeadlessClaudeAssistant` |
| `Notifier` | `adapter/DesktopNotifier` — a channel; `notify/Notifications` fans out to every one it finds |
| `Processes` | `adapter/ProcessRunner` |
| `SessionHost` | `adapter/tmux/TmuxSessionHost` — the one seam with a single impl, not selectable by config |
| `SessionLog` | `adapter/agent/ClaudeSessionLog` — what a session's own log says it last did |
| `WorktreeProcesses` | `adapter/LsofWorktreeProcesses` |
| `UserNotifier` | `adapter/macos/MacNotifier`, `adapter/linux/LibNotifyNotifier` |
| `TerminalDriver` | `adapter/AbstractKittyTerminalDriver` (+ one per platform) |
| `EditorDriver` | `adapter/CliEditorDriver` |
| `StartupCheck` | three in `startup/`, and six at the edge — see [Assembly validation](#assembly-validation) |
| `AgentWorktree`, `Notification` | nothing: they are value **records** the interfaces above pass |

## Where a new thing goes

| you want to | do this |
|-------------|---------|
| add a per-task verb | a constant in `flow/TaskAction`, a class in `capability/`, **and** a rule in `flow/FlowRules` |
| add a report | one `GlobalCommand` in `command/` with `report()` true — the board picks it up |
| add a launch shortcut | a `GlobalCommand`, plus an endpoint and a form for the board |
| replace a built-in verb | another `TaskCapability` for the same action with a higher `priority()` |
| run something before/after a verb | a `port/CapabilityInterceptor` for that action — never a new status |
| add unattended work | one `Job`; `Jobs` tickers it and the `jobs` report lists it |
| notify somewhere else | one `Notifier` in `adapter/`; `Notifications` finds it and no caller changes |
| support another host, tracker, agent or OS | one adapter behind its port, plus the `@ConditionalOnProperty` value |

- A new verb reaches the board because the board is generated from `flow/TaskAction`: it renders `Move.actions()` (the
  legal ones only, `FlowRules.allowed(...)`), and `FlowEngine` refuses an illegal one with a sentence.
- **The surface holds no list** — the usual way to break that is building a button in `static/ui/card.js`.
- An action the table never mentions is offered by nobody, so `startup/FlowCheck` refuses to start.
- A capability gets a folder once it owns work (`capability/ship/`); five verbs are still flat.
- Two equal `priority()` values are refused rather than ordered arbitrarily.
- `do` and `resume` are named literally in `app.js` and `TaskCommandsController`, so a new launch shortcut costs the
  board a change.

## The board is two rings too

`surface/src/main/resources/static` is vanilla HTML/CSS/JS served from the jar, no external asset of any kind
([`docs/rules/design.md`](docs/rules/design.md)). Native ES modules, split by the backend's rule: `core/` answers a
question without owning a node, `ui/` owns the nodes it renders, `app.js` only wires them.

| file | the question it answers |
|------|------------------------|
| `core/api` | how the backend is called, and what a refusal carries |
| `core/store` | what the page knows; only `set` writes it |
| `core/format` | the two clocks, mirroring `DurationFormat` |
| `core/dom` | how an element is built — never markup: ids come from a hand-edited state file |
| `ui/render` | one repaint from the snapshot, and the one delegated click on the grid |
| `ui/card`, `ui/header` | what a task looks like, and the line above the grid — pure, no listeners |
| `ui/filters`, `ui/projects` | the controls that narrow, and the project picker |
| `ui/act`, `ui/inflight`, `ui/confirm` | running one action: what is in flight, what it asks first |
| `ui/submit`, `ui/launch`, `ui/resume` | one submit pipeline, and the two forms that use it |
| `ui/palette`, `ui/keys` | ⌘K's two tiers, and every key binding in one table |
| `ui/dialogs`, `ui/toast`, `ui/tips` | what opens over the board, what is said in passing, what a hover shows |
| `ui/legend` | what every mark means, as the page's own elements — the one place they are defined |
| `ui/refresh` | how the page learns a task changed, and the one clock it ticks itself |

- **A module owns its own nodes** — never `getElementById` for another module's; one needing another to act takes a
  callback at wiring time (`palette.wire`, `header.onNarrow`, `toast.showLog`).
- **Rendering is pure and total.** A card is a function of the task, the grid is `replaceChildren`, and no card carries
  a closure — a button carries `data-action`/`data-report` and `ui/render` delegates the one listener.
- **One answer per question.** One transport, one toast, one submit pipeline, one key table.
- **A board file over ~150 lines is the signal to split**, not to add a section comment.

## The agent system is a ring

The coding agent is an adapter; the inner rings know one port — launch a session in a worktree, provision it, name the
file it reads its instructions from.

- **No shared instruction file is named after one vendor.** The system-knowledge file is `AGENTS.md`
  (`port/AgentRuntime.SYSTEM_KNOWLEDGE_FILE`), in a provisioned worktree and in this root, where `CLAUDE.md` links to it
  — one file, never two copies to drift.
- **How an agent reaches the MCP server is the adapter's business** — direct HTTP with a working-directory header, or a
  stdio bridge for a CLI that can only spawn a server. Nothing above the port knows which.
- **What a session is doing is read, never asked**: the log a CLI keeps answers liveness (`lastSessionActivity`) and its
  own hooks report a stop (`surface/agent`) — no model in either path, so a session out of tokens still reaches the
  board. Which events a CLI reports is a resource (`adapter/…/resources/hooks/`), not Java.
- **What a session may push is refused below the CLI as well**: `service/WorktreeHooks` writes a `pre-push` into the
  worktree's own `.jagt/hooks` and the launch command points that session's git at it, so a CLI with no hooks of its own
  meets it too. Nothing goes into the repository ([`docs/rules/git.md`](docs/rules/git.md)).
- **The one-shot assistant is a separate port**: an install may run a local model for text-to-command and a vendor CLI
  for the sessions. It is the only place jagt spends money, so it is metered.

## Assembly validation

At startup, **one report of everything wrong**, never first-failure, because a half-valid assembly boots and then does
nothing: `startup/StartupCheck` implementations are collected by `startup/StartupValidation`, which throws
`Misconfigured` once with every problem. Nine of them — three in `startup/` (`Config`, `Flow`, `Workspace`), and six at
the edge, which can name the key that fixes it: `adapter/ToolchainCheck`, `adapter/PlatformCheck`,
`adapter/CliEditorDriver`, `adapter/AbstractKittyTerminalDriver`, `adapter/agent/CodexAgentRuntime`,
`adapter/linux/LibNotifyNotifier`. The composition half, and what of it exists:

| # | check | state |
|---|-------|-------|
| 1 | every flow rule names a registered capability | run time only, and as an `IllegalStateException` — a 500, not a sentence |
| 2 | every status something can put a task into | built (`startup/FlowCheck`) |
| 3 | every (status × capability × outcome) decided | planned — needs a capability to declare its outcomes |
| 4 | a job's declared capability and watched statuses exist | planned — `Job` declares neither yet |
| 5 | a required port capability present in the selected adapter | weak form: a configured type resolves to a bean, and the platform it was picked for is the one jagt runs on (`adapter/PlatformCheck`) |
| 6 | one owner per id | built for all three registries |

- No "stuck status" check beside #2, deliberately: the report door judges the status being reported, not the one being
  left, so no status can trap a task.
- #5 checks nothing about thread resolution, tab titles or an attachable session host.
- #6 refuses to start on a duplicate job id, a duplicate command verb or an equal capability priority.
- **The refusing half exists; the degrading half does not** — a missing kitty refuses rather than falling back.

## The build

Five Gradle modules under `orchestrator-backend/`, one per ring, so the dependency rule is the compiler's: an import
that points outward does not compile. Two folders inside one module it cannot separate — hence `RingsTest`.

| module | holds | sees |
|--------|-------|------|
| `:core` | `task/` `flow/` `port/` | nothing of jagt's, no Spring, no Lombok |
| `:usecase` | `capability/` `command/` `job/` `notify/` `service/` `config/` `startup/` | `:core` |
| `:adapter` | `adapter/` — the only place an OS is named | `:core`, `:usecase` |
| `:surface` | `surface/` | `:core`, `:usecase` |
| root | `OrchestratorApplication`, `FlowWiring`, `application.yml` | all four — the assembly |

- Tests live with what they test: `:core:test` (no container at all), `:usecase:test`, `:adapter:test` (real git, real
  `lsof`, real binaries), `:surface:test`, and the root's own few that assert what the install ships.
- The exception is a `:usecase` class a real binary is the only honest test of — `GitServiceTest`,
  `TaskProvisioningTest` and `WorktreeHooksTest` live in `:adapter:test`, where git is.
- Four suites stay out of `check`: `e2eTest` (git + tmux, real worktrees), `boardTest` (the page in a real browser),
  `linuxDriverTest` (Linux binaries, container) and `promptEval` (the assistant's CLI, and tokens).
- `./gradlew test e2eTest boardTest` is everything runnable here; `./gradlew build stageJar && java -jar
  build/libs/jagt-run.jar` starts the board.
- `config/` keeps what the human wrote and the edge resolves a binary where it spawns it, so no use case names
  `adapter/Executables`.
