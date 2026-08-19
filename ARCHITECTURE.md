# jagt — architecture

For every agent and every human that opens this repository. The RULES live in `AGENTS.md`, the SITUATIONS in
`USE-CASES.md`; this file answers only "what kinds of thing does jagt have, and where does a new one go".

Everything below is a package under `dev.jagt.orchestrator`, in one of five Gradle modules inside
`orchestrator-backend/` — which is also where `gradlew` lives, NOT the repository root. A folder written `flow/`
is therefore `orchestrator-backend/core/src/main/java/dev/jagt/orchestrator/flow`.

STATUS IS PART OF THE MAP, and several sessions edit this tree at once — so every claim here is written to be
checkable, and a row that says `built` means built. Last checked against the tree on 2026-08-19. When you find a
row that has gone stale, change it; do not delete it to make the file tidy.

## The law

Anything that runs, tells, decides, or is done to a task is one of the KINDS. A kind is one interface — in `port/`
when something outside implements it — one registry, and one folder of implementations. If something fits no kind,
the kind is missing: add a kind, never an exception.

## The kinds

| Kind | Its one job | State |
|---|---|---|
| `task/` | the task record and everything it is made of | built; `service/StateService` writes it to `state.json` |
| `flow/` | which status allows what, and where each outcome leads | built — `FlowRules`, `FlowEngine`, `FlowReports`, `Move` |
| `capability/` | one thing that can be done to a task | built — a class per verb; `ship`/`deploy`/`done` own their work too |
| `job/` | work that runs with nobody watching | built — `Job` + `Jobs`; the five implementations still live in `service/` |
| `notify/` | something a human must be told | built — the fan-out; the record and the channel contract are `port/Notification` + `port/Notifier` |
| `surface/` | who is asking | built — `surface/console`, `surface/board`, `surface/mcp`, `surface/ui` |
| `command/` | what a human asks that no task owns | built — `GlobalCommand` + `GlobalCommands`, one class per verb, and the reports they render |

`service/` is the rest: work more than one kind shares — git, the state file, config reading, worktrees and agent
sessions. The console and the plain-text endpoints share `command/StateViews` and `service/DashboardRenderer`; the
board renders `flow/TaskView` instead. It is named for what it is
rather than pushed into one verb's folder, because a class two kinds use belongs to neither.

## The rings — arrows point inward only

```
adapter/                     the only place an OS is named, and where every vendor lives
    | implements
port/  task/  flow/          the centre: records, rules, and the interfaces they declare
    ^ used by
capability/ command/ job/ notify/ service/ surface/     the use cases and the work they share
```

`port/` is INSIDE the centre, not a ring around it: those interfaces are written by the rules for their own needs,
and `flow/` depends on them (`TaskStore`, `AgentPresence`, `TaskCapability`, `CapabilityInterceptor`).

What `RingsTest` actually asserts, and nothing more:

- `task/`, `flow/` and `port/` name no other ring — as a plain import, a static import, or a fully qualified name
  written inline.
- those three import no Spring and no Lombok. Jackson annotations stay: the record IS the file format.
- no OS name (`osascript`, `notify-send`, `setsid`, an install prefix, a Windows shell) outside `adapter/`.

- no ring between the centre and the edge names `adapter/` — a use case that names the edge is the rule backwards.
- it can prove it READ every ring, because a root that stops matching after a refactor is how this test goes green
  and quietly stops guarding anything.

What it cannot see: resources and scripts, the words a host invents that its list does not carry, and a cycle
between two SIBLING folders (`command/` and `service/` are both use cases, and the compiler does not separate
them). The generic keys `orchestrator.tmux-command` and `orchestrator.kitty-command` still name vendors — each is
read by one adapter, and renaming a key a human has already written is the owner's call.

## No capability WRITES a status, and `withStatus` has two callers

A capability does the work and reports an OUTCOME; which status that leads to is the table's answer. `withStatus`
has exactly two callers outside the record itself — the two doors below. Two honest exceptions: a task is BORN at
NEW (`service/TaskProvisioning` builds it that way), and one capability READS a status because it must know it is
resuming (`capability/deploy/DeployService` looks for DEPLOY_CONFLICT).

```
TaskCapability:  Outcome run(taskId)                    // no TaskStatus in this file
Outcome:         OK | NOTHING | RELAYED | CONFLICT | PARTIAL | GONE, + the sentence, the stamp and the cause
FlowRules:       rule(DEPLOY).from(<statuses>).when(<guard>).on(OK, DEPLOYED).on(CONFLICT, DEPLOY_CONFLICT).add()
Facts:           the guard's second argument — an open request, and a liveness probe passed as a SUPPLIER:
                 a card uses Facts.projected (assume not live, costs nothing) and the gate the real probe,
                 which is why a stuck SHIPPING card offers SHIP and the gate can still refuse it
FlowEngine:      rules.allows? -> capability.run -> rules.next(outcome) -> ONE status write   (door one)
FlowReports:     a status the task itself reports — its own gate, FlowRules.reportable        (door two)
```

Three things the sketch does not show:

- a declaration without `.add()` is not in the table. `FlowRules.allows(...)` is the QUERY the engine and the
  projection call, not the way to declare a rule.
- an outcome with no stamp keeps the line the task already carries, and the transition still happens if the table
  names one. PARTIAL is the only kind that REFUSES: it is stamped first and thrown second, because a shared branch
  holding half a change must be recorded, not merely complained about.
- an interceptor (`port/CapabilityInterceptor`) wraps a verb and may refuse. Refusing stops the work AND the
  transition, because then nothing happened.

## Which class answers which port

| Port | Implemented by |
|---|---|
| `TaskStore` | `service/StateService` |
| `AgentPresence` | `service/AgentSessions` |
| `TaskCapability` | one per verb under `capability/` |
| `CapabilityInterceptor` | nothing yet — the seam is there for an install to add its own step |
| `AgentRuntime` | `adapter/agent/{Claude,Codex,Stub}AgentRuntime` (+ `AbstractAgentRuntime`) |
| `CodeHost` | `adapter/codehost/{GitLab,GitHub}CodeHost` |
| `Tracker` | `adapter/tracker/JiraTracker` |
| `MasterAssistant` | `adapter/assistant/HeadlessClaudeAssistant` |
| `Notifier` | `adapter/DesktopNotifier` (a channel; `notify/Notifications` fans out to every one it finds) |
| `Processes` | `adapter/ProcessRunner` |
| `SessionHost` | `adapter/tmux/TmuxSessionHost` — the one seam with a single implementation, and not selectable by config |
| `WorktreeProcesses` | `adapter/LsofWorktreeProcesses` |
| `WebTerminal` | `adapter/TtydWebTerminal` |
| `StartupCheck` | `startup/{Config,Flow,OutsideReads,Workspace}Check`, and six at the edge: `adapter/{ToolchainCheck,TtydWebTerminal,CliEditorDriver,AbstractKittyTerminalDriver}`, `adapter/agent/CodexAgentRuntime`, `adapter/linux/LibNotifyNotifier` |
| `UserNotifier` | `adapter/macos/MacNotifier`, `adapter/linux/LibNotifyNotifier` |
| `TerminalDriver` | `adapter/AbstractKittyTerminalDriver` (+ per platform), `adapter/macos/WarpTerminalDriver` |
| `EditorDriver` | `adapter/CliEditorDriver` |
| `JsonHttp` | `adapter/http/RestClientJsonHttp` |

## Where a new thing goes

| You want to | Do this |
|---|---|
| add a per-task verb | a constant in `flow/TaskAction`, a class in `capability/` returning it (a folder once it owns work — `capability/ship/`; five verbs are still flat), AND a rule in `flow/FlowRules`: an action the table never mentions is offered by nobody and refused forever. `startup/FlowCheck` refuses to start rather than let that be silent |
| add a report | one `GlobalCommand` in `command/` with `report()` true — both surfaces pick it up, no page change |
| add a launch shortcut | a `GlobalCommand` covers the console and the palette; the board also needs an endpoint and a form, because `do` and `resume` are named literally in `app.js` and `TaskCommandsController` |
| replace a built-in verb | another `TaskCapability` for the same action with a higher `priority()`; an equal priority is refused |
| run something before/after a verb | a `port/CapabilityInterceptor` for that action — never a new status |
| add unattended work | one `Job`; `Jobs` tickers it and the `jobs` report lists it. An adapter's own workaround is a job THAT adapter contributes |
| notify somewhere else | one `Notifier` in `adapter/`; `Notifications` finds it and no caller changes |
| support another host, tracker, agent or OS | one adapter behind the port it answers, plus the `@ConditionalOnProperty` value that selects it — and `startup/OutsideReadsCheck` has to accept the new type |

## Parity is an invariant, and this is how it is kept

`flow/Move` answers "what can be done to this task, and whose turn is it"; `flow/TaskView` is what the board
renders; `service/TaskViews` builds both. A new verb reaches both surfaces because both are generated from the
`flow/TaskAction` declaration: the board renders `Move.actions()` — the legal ones only, which is
`FlowRules.allowed(...)` — while the console offers every verb and `FlowEngine` refuses an illegal one with a
sentence. Neither surface holds a list, and the usual way to break that is to add markup to
`surface/src/main/resources/static/app.js` instead of a declaration.

## The agent system is a ring, not the centre

The coding agent is an adapter: Codex, a vendor CLI, a local model. The inner rings know one port — launch a
session in a worktree, provision it, name the file it reads its instructions from.

- NO SHARED INSTRUCTION FILE IS NAMED AFTER ONE VENDOR. The system-knowledge file is `AGENTS.md`
  (`port/AgentRuntime.SYSTEM_KNOWLEDGE_FILE`) both in a worktree jagt provisions and in this repository's own root,
  where `CLAUDE.md` is a link to it — one file, never two copies to drift.
- HOW AN AGENT REACHES THE MCP SERVER IS THE ADAPTER'S BUSINESS (direct HTTP with a working-directory header, or a
  stdio bridge for a CLI that can only spawn a server). Nothing above the port knows which.
- THE ONE-SHOT ASSISTANT IS A SEPARATE PORT from the session runtime: an install may run a local model for
  text-to-command and a vendor CLI for the sessions. It is also the only place jagt spends money, so it is metered.

## Assembly validation

At startup, one report of everything wrong — never first-failure, because a half-valid assembly boots and then does
nothing. `startup/StartupCheck` implementations are collected by `startup/StartupValidation`, which throws
`Misconfigured` once with every problem; six of them live at the EDGE, because a driver knows what its own binary
needs and names the key that would fix it.

The composition half, and what of it exists:

1. every flow rule names a registered capability — RUN time only today, and as an `IllegalStateException` rather
   than a refusal, so it surfaces as a 500 rather than a sentence
2. every status something can put a task into — built (`startup/FlowCheck`). There is deliberately no "stuck
   status" check beside it: the report door is judged by the status being reported rather than the one being left,
   so no status can trap a task and asserting it would assert nothing
3. every (status × capability × outcome) decided — planned, and it needs a capability to declare which outcomes it
   can return
4. a job's declared capability and watched statuses exist — planned, and `Job` declares neither yet
5. a required port capability present in the SELECTED adapter — only the weak form (a configured type resolves to a
   bean); nothing checks thread resolution, tab titles or an attachable session host
6. one owner per id — built for all three registries: a duplicate job id, a duplicate command verb and an equal
   capability priority each refuse to start

The refusing half EXISTS: six edge checks refuse to start and name the config key that would fix it. The degrading
half does not — a missing kitty refuses rather than falling back to another terminal.

## The build

FIVE Gradle modules under `orchestrator-backend/`, one per ring, so the dependency rule is the compiler's:

```
:core      task/ flow/ port/          no dependency on anything of jagt's, no Spring, no Lombok
:usecase   capability/ command/ job/ notify/ service/ config/ startup/     sees :core
:adapter   adapter/                   sees :core and :usecase; the only place an OS is named
:surface   surface/                   sees :core and :usecase
 root      OrchestratorApplication, FlowWiring, application.yml            sees all four — the assembly
```

An import that points outward does not compile: `:core` cannot name a use case, and neither `:adapter` nor
`:surface` can name each other. What the modules cannot separate is two folders inside the same one, which is why
`RingsTest` still exists.

Tests live with what they test: `:core:test` (no container at all), `:usecase:test`, `:adapter:test` (drives real
git, a real `lsof`, real binaries), `:surface:test`, and the root's own few that assert what the INSTALL ships.
Three suites stay out of `check` because they need a machine: `e2eTest` (git + tmux, real worktrees — source set
`e2e`), `boardTest` (the page in a real browser), `linuxDriverTest` (Linux binaries, container). Run everything
with `./gradlew test e2eTest boardTest` from `orchestrator-backend/`; run the app with
`./gradlew build stageJar` then `java -jar build/libs/jagt-run.jar`.

The cycle that used to block the split is gone: `config/` keeps what the human wrote and the EDGE resolves a
binary where it spawns it, so nothing in the use cases names `adapter/Executables` any more.
