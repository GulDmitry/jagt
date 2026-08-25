# Architecture

The map of the code: **what kinds of thing jagt has, and where a new one goes.**
The rules live in [`AGENTS.md`](AGENTS.md), the situations in [`USE-CASES.md`](USE-CASES.md).

- [The law](#the-law)
- [The kinds](#the-kinds)
- [The rings](#the-rings)
- [The flow machine](#the-flow-machine)
- [Ports and who answers them](#ports-and-who-answers-them)
- [Where a new thing goes](#where-a-new-thing-goes)
- [How parity is kept](#how-parity-is-kept)
- [The board is two rings too](#the-board-is-two-rings-too)
- [The agent system is a ring](#the-agent-system-is-a-ring)
- [Assembly validation](#assembly-validation)
- [The build](#the-build)

Every path below is a package under `dev.jagt.orchestrator`, inside `orchestrator-backend/` — which is also
where `gradlew` lives, **not** the repository root. So `flow/` means
`orchestrator-backend/core/src/main/java/dev/jagt/orchestrator/flow`.

> [!NOTE]
> Status is part of the map, and several sessions edit this tree at once. Every claim here is written to be
> checkable, and a row that says *built* means built. Last checked against the tree on 2026-08-19. Found a
> stale row? Change it — do not delete it to make the file tidy.

## The law

Anything that runs, tells, decides, or is done to a task is one of the **kinds**. A kind is one interface — in
`port/` when something outside implements it — one registry, and one folder of implementations.

**If something fits no kind, the kind is missing. Add a kind, never an exception.**

## The kinds

| kind | its one job | state |
|------|-------------|-------|
| `task/` | the task record and everything it is made of | built — `service/StateService` writes it to `state.json` |
| `flow/` | which status allows what, and where each outcome leads | built — `FlowRules`, `FlowEngine`, `FlowReports`, `Move` |
| `capability/` | one thing that can be done to a task | built — a class per verb |
| `job/` | work that runs with nobody watching | built — `Job` + `Jobs`; the five impls still live in `service/` |
| `notify/` | something a human must be told | built — the fan-out; the contract is `port/Notification` + `port/Notifier` |
| `surface/` | who is asking | built — `console`, `board`, `mcp`, `agent`, `ui` |
| `command/` | what a human asks that no task owns | built — `GlobalCommand` + `GlobalCommands`, one class per verb |

`service/` is the rest: work more than one kind shares — git, the state file, config, worktrees, agent
sessions. It is named for what it is rather than pushed into one verb's folder, because a class two kinds use
belongs to neither.

The console and the plain-text endpoints share `command/StateViews` and `service/DashboardRenderer`; the board
renders `flow/TaskView` instead.

## The rings

Arrows point **inward only**.

```
adapter/                                   the only place an OS is named, and where every vendor lives
    │ implements
port/  task/  flow/                        the centre: records, rules, and the interfaces they declare
    ▲ used by
capability/ command/ job/ notify/ service/ surface/     the use cases and the work they share
```

`port/` is *inside* the centre, not a ring around it: those interfaces are written by the rules for their own
needs, and `flow/` depends on them (`TaskStore`, `AgentPresence`, `TaskCapability`, `CapabilityInterceptor`).

`RingsTest` asserts exactly this, and nothing more:

- `task/`, `flow/` and `port/` name no other ring — as an import, a static import, or a fully qualified name.
- those three import no Spring and no Lombok. Jackson annotations stay: the record **is** the file format.
- no OS name (`osascript`, `notify-send`, `setsid`, an install prefix, a Windows shell) outside `adapter/`.
- no ring between the centre and the edge names `adapter/` — a use case that names the edge is the rule backwards.
- it can prove it read every ring. A root that stops matching after a refactor is how this test goes green and
  quietly stops guarding anything.

What it cannot see: resources and scripts, the words a host invents that its list does not carry, and a cycle
between two *sibling* folders (`command/` and `service/` are both use cases, and the compiler does not separate
them). `orchestrator.tmux-command` and `orchestrator.kitty-command` still name vendors — each is read by one
adapter, and renaming a key a human has already written is the owner's call.

## The flow machine

**No capability writes a status.** It does the work and reports an outcome; which status that leads to is the
table's answer.

```
TaskCapability:  Outcome run(taskId)                    // no TaskStatus in this file
Outcome:         OK | NOTHING | RELAYED | CONFLICT | PARTIAL | GONE, + the sentence, the stamp, the cause
FlowRules:       rule(DEPLOY).from(<statuses>).when(<guard>).on(OK, DEPLOYED).on(CONFLICT, DEPLOY_CONFLICT).add()
Facts:           the guard's second argument — an open request, and a liveness probe passed as a SUPPLIER
FlowEngine:      rules.allows? → capability.run → rules.next(outcome) → ONE status write      (door one)
FlowReports:     a status the task itself reports — its own gate, FlowRules.reportable        (door two)
```

`withStatus` has exactly two callers outside the record itself: the two doors. Two honest exceptions — a task
is born at NEW (`service/TaskProvisioning`), and one capability *reads* a status because it must know it is
resuming (`capability/deploy/DeployService` looks for DEPLOY_CONFLICT).

Three things the sketch does not show:

- **A declaration without `.add()` is not in the table.** `FlowRules.allows(...)` is the query the engine and
  the projection call, not the way to declare a rule.
- **An outcome with no stamp keeps the line the task already carries**, and the transition still happens if the
  table names one. PARTIAL is the only kind that refuses: stamped first, thrown second, because a shared branch
  holding half a change must be recorded, not merely complained about.
- **An interceptor** (`port/CapabilityInterceptor`) wraps a verb and may refuse. Refusing stops the work *and*
  the transition, because then nothing happened.

Why `Facts` passes liveness as a supplier: a card uses `Facts.projected` (assume not live, costs nothing) while
the gate uses the real probe. That is why a stuck SHIPPING card offers SHIP and the gate can still refuse it.

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
| `WorktreeProcesses` | `adapter/LsofWorktreeProcesses` |
| `WebTerminal` | `adapter/TtydWebTerminal` |
| `UserNotifier` | `adapter/macos/MacNotifier`, `adapter/linux/LibNotifyNotifier` |
| `TerminalDriver` | `adapter/AbstractKittyTerminalDriver` (+ per platform), `adapter/macos/WarpTerminalDriver` |
| `EditorDriver` | `adapter/CliEditorDriver` |
| `JsonHttp` | `adapter/http/RestClientJsonHttp` |
| `StartupCheck` | four in `startup/`, and seven at the edge — see [Assembly validation](#assembly-validation) |

## Where a new thing goes

| you want to | do this |
|-------------|---------|
| add a per-task verb | a constant in `flow/TaskAction`, a class in `capability/`, **and** a rule in `flow/FlowRules` |
| add a report | one `GlobalCommand` in `command/` with `report()` true — both surfaces pick it up |
| add a launch shortcut | a `GlobalCommand`, plus an endpoint and a form for the board |
| replace a built-in verb | another `TaskCapability` for the same action with a higher `priority()` |
| run something before/after a verb | a `port/CapabilityInterceptor` for that action — never a new status |
| add unattended work | one `Job`; `Jobs` tickers it and the `jobs` report lists it |
| notify somewhere else | one `Notifier` in `adapter/`; `Notifications` finds it and no caller changes |
| support another host, tracker, agent or OS | one adapter behind its port, plus the `@ConditionalOnProperty` value |

Four things those rows do not fit:

- An action the table never mentions is offered by nobody and refused forever. `startup/FlowCheck` refuses to
  start rather than let that be silent.
- A capability gets a folder once it owns work (`capability/ship/`); five verbs are still flat.
- Two equal `priority()` values are refused rather than ordered arbitrarily.
- `do` and `resume` are named literally in `app.js` and `TaskCommandsController`, which is why a new launch
  shortcut costs the board a change.

## How parity is kept

| class | answers |
|-------|---------|
| `flow/Move` | what can be done to this task, whose turn it is, how loudly it asks |
| `flow/Owner` | whose turn |
| `flow/Attention` | whether the card is an interruption — one value for the badge, the count and the filter |
| `flow/TaskView` | what the board renders |
| `service/TaskViews` | builds both |

A new verb reaches both surfaces because both are generated from the `flow/TaskAction` declaration: the board
renders `Move.actions()` (the legal ones only, i.e. `FlowRules.allowed(...)`), while the console offers every
verb and `FlowEngine` refuses an illegal one with a sentence.

**Neither surface holds a list.** The usual way to break that is to build a button in `static/ui/card.js`
instead of declaring an action.

## The board is two rings too

`surface/src/main/resources/static` is vanilla HTML/CSS/JS served from the jar — no build step, no CDN,
nothing fetched from a network. Native ES modules, split by the same rule as the backend: `core/` answers a
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

Four rules hold it together. Each replaced a bug that had already happened:

- **A module owns its own nodes.** Nobody calls `getElementById` for somebody else's element; a module that
  needs another one to act takes a callback at wiring time (`palette.wire`, `header.onNarrow`, `toast.showLog`).
- **Rendering is pure and total.** A card is a function of the task, the grid is `replaceChildren`, and no card
  carries a closure — a button carries `data-action`/`data-report` and `ui/render` delegates the one listener.
- **One answer per question.** One transport, one toast, one submit pipeline, one key table. Three copies of
  the submit dance is how a fix reaches one form and is forgotten in the next.
- **A file over ~150 lines is the signal to split**, not to add a section comment.

## The agent system is a ring

The coding agent is an adapter: Codex, a vendor CLI, a local model. The inner rings know one port — launch a
session in a worktree, provision it, name the file it reads its instructions from.

- **No shared instruction file is named after one vendor.** The system-knowledge file is `AGENTS.md`
  (`port/AgentRuntime.SYSTEM_KNOWLEDGE_FILE`), both in a provisioned worktree and in this repository's root,
  where `CLAUDE.md` is a link to it. One file, never two copies to drift.
- **How an agent reaches the MCP server is the adapter's business** — direct HTTP with a working-directory
  header, or a stdio bridge for a CLI that can only spawn a server. Nothing above the port knows which.
- **What a session is doing is read, never asked.** The log a CLI keeps of a session answers liveness
  (`lastSessionActivityMillis`) and its own hooks report a stop (`surface/agent`) — no model in either path, so
  a session out of tokens still reaches the board. Which events a CLI reports is a resource
  (`adapter/…/resources/hooks/`), not a table in Java.
- **The one-shot assistant is a separate port** from the session runtime: an install may run a local model for
  text-to-command and a vendor CLI for the sessions. It is also the only place jagt spends money, so it is
  metered.

## Assembly validation

At startup, **one report of everything wrong** — never first-failure, because a half-valid assembly boots and
then does nothing. `startup/StartupCheck` implementations are collected by `startup/StartupValidation`, which
throws `Misconfigured` once with every problem.

Eleven checks: four in `startup/` (`Config`, `Flow`, `OutsideReads`, `Workspace`) and seven at the edge, because
a driver knows what its own binary needs and can name the key that fixes it — `adapter/ToolchainCheck`,
`adapter/PlatformCheck`, `adapter/TtydWebTerminal`, `adapter/CliEditorDriver`,
`adapter/AbstractKittyTerminalDriver`, `adapter/agent/CodexAgentRuntime`, `adapter/linux/LibNotifyNotifier`.

The composition half, and what of it exists:

| # | check | state |
|---|-------|-------|
| 1 | every flow rule names a registered capability | run time only, and as an `IllegalStateException` — a 500, not a sentence |
| 2 | every status something can put a task into | built (`startup/FlowCheck`) |
| 3 | every (status × capability × outcome) decided | planned — needs a capability to declare its outcomes |
| 4 | a job's declared capability and watched statuses exist | planned — `Job` declares neither yet |
| 5 | a required port capability present in the selected adapter | weak form: a configured type resolves to a bean, and the platform it was picked for is the one jagt runs on (`adapter/PlatformCheck`) |
| 6 | one owner per id | built for all three registries |

There is deliberately **no** "stuck status" check beside #2: the report door is judged by the status being
reported rather than the one being left, so no status can trap a task and asserting it would assert nothing.

#5 checks nothing about thread resolution, tab titles or an attachable session host. #6 refuses to start on a
duplicate job id, a duplicate command verb or an equal capability priority.

**The refusing half exists; the degrading half does not.** Seven edge checks refuse to start and name the key
that would fix it. A missing kitty refuses rather than falling back to another terminal.

## The build

Five Gradle modules under `orchestrator-backend/`, one per ring, so the dependency rule is the compiler's:

| module | holds | sees |
|--------|-------|------|
| `:core` | `task/` `flow/` `port/` | nothing of jagt's, no Spring, no Lombok |
| `:usecase` | `capability/` `command/` `job/` `notify/` `service/` `config/` `startup/` | `:core` |
| `:adapter` | `adapter/` — the only place an OS is named | `:core`, `:usecase` |
| `:surface` | `surface/` | `:core`, `:usecase` |
| root | `OrchestratorApplication`, `FlowWiring`, `application.yml` | all four — the assembly |

An import that points outward does not compile: `:core` cannot name a use case, and `:adapter` and `:surface`
cannot name each other. What the modules cannot separate is two folders inside the same one — which is why
`RingsTest` still exists.

Tests live with what they test: `:core:test` (no container at all), `:usecase:test`, `:adapter:test` (real
git, real `lsof`, real binaries), `:surface:test`, and the root's own few that assert what the install ships.

Three suites stay out of `check` because they need a machine: `e2eTest` (git + tmux, real worktrees),
`boardTest` (the page in a real browser), `linuxDriverTest` (Linux binaries, container).

```sh
cd orchestrator-backend
./gradlew test e2eTest boardTest          # everything runnable here
./gradlew build stageJar && java -jar build/libs/jagt-run.jar
```

The cycle that used to block the split is gone: `config/` keeps what the human wrote and the edge resolves a
binary where it spawns it, so nothing in the use cases names `adapter/Executables` any more.
