# jagt — architecture

For every agent and every human that opens this repository. The RULES live in `AGENTS.md`, the SITUATIONS in
`USE-CASES.md`; this file answers only "what kinds of thing does jagt have, and where does a new one go".

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
| commands | what a human asks that no task owns | built — `service/GlobalCommand` + `service/commands/`; not a folder of its own yet |

`service/` is the rest: work more than one kind shares — git, the state file, config reading, tmux, worktrees, and
the renderers both surfaces use. It is named for what it is rather than pushed into one verb's folder, because a
class two capabilities use belongs to neither. It is the largest package here and the least sorted.

## The rings — arrows point inward only

```
adapter/                     the only place an OS is named, and where every vendor lives
    | implements
port/  task/  flow/          the centre: records, rules, and the interfaces they declare
    ^ used by
capability/ job/ notify/ service/ surface/       the use cases and the work they share
```

`port/` is INSIDE the centre, not a ring around it: those interfaces are written by the rules for their own needs,
and `flow/` depends on them (`TaskStore`, `AgentPresence`, `TaskCapability`, `CapabilityInterceptor`).

What `RingsTest` actually asserts, and nothing more:

- `task/`, `flow/` and `port/` name no other ring — as a plain import, a static import, or a fully qualified name
  written inline.
- those three import no Spring and no Lombok. Jackson annotations stay: the record IS the file format.
- no OS name (`osascript`, `notify-send`, `setsid`, an install prefix, a Windows shell) outside `adapter/`.

Outward imports from the OUTER rings are not asserted yet, and there are two: `surface/board/AgentTerminalController`
names `adapter/TtydWebTerminal`, and `service/GitService` + `service/TmuxService` name `adapter/ProcessRunner`. Two
vendors are also still outside `adapter/`: tmux has no port at all (`service/TmuxService`), and the config key
`orchestrator.claude-command` names one agent.

## No capability knows a status, and only `flow/` writes one

A capability does the work and reports an OUTCOME; which status that leads to is the table's answer. `withStatus`
has exactly two callers outside the record itself — the two doors below.

```
TaskCapability:  Outcome run(taskId)                    // no TaskStatus in this file
Outcome:         OK | NOTHING | RELAYED | CONFLICT | PARTIAL | GONE, + the sentence and the stamp
FlowRules:       rule(DEPLOY).from(<statuses>).when(<guard>).on(OK, DEPLOYED).on(CONFLICT, DEPLOY_CONFLICT).add()
FlowEngine:      rules.allows? -> capability.run -> rules.next(outcome) -> ONE status write   (door one)
FlowReports:     a status the task itself reports, same table, same refusals                  (door two)
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
| `UserNotifier` | `adapter/macos/MacNotifier`, `adapter/linux/LibNotifyNotifier` |
| `TerminalDriver` | `adapter/AbstractKittyTerminalDriver` (+ per platform), `adapter/macos/WarpTerminalDriver` |
| `EditorDriver` | `adapter/CliEditorDriver` |
| `JsonHttp` | `adapter/http/RestClientJsonHttp` |

## Where a new thing goes

| You want to | Do this |
|---|---|
| add a per-task verb | a class in `capability/` — or a folder once it owns work (`capability/ship/` is the pattern; six verbs are still flat), plus rules in `flow/FlowRules` if it moves the task |
| add a report or a launch shortcut | one `GlobalCommand` in `service/commands/` — every surface picks it up |
| replace a built-in verb | another `TaskCapability` for the same action with a higher `priority()`; an equal priority is refused |
| run something before/after a verb | a `port/CapabilityInterceptor` for that action — never a new status |
| add unattended work | one `Job`; `Jobs` tickers it and the `jobs` report lists it. An adapter's own workaround is a job THAT adapter contributes |
| notify somewhere else | one `Notifier` in `adapter/`; `Notifications` finds it and no caller changes |
| support another host, tracker, agent or OS | one adapter behind the port it answers |

## Parity is an invariant, and this is how it is kept

`flow/Move` answers "what can be done to this task, and whose turn is it"; `flow/TaskView` is what a surface
renders; `service/TaskViews` builds both. Every per-task verb reaches both surfaces because both render
`Move.actions()`, which is `FlowRules.allowed(...)`. A capability that exists on one surface only is a bug, and the
usual way to create one is to add markup instead of a declaration.

## The agent system is a ring, not the centre

The coding agent is an adapter: Codex, a vendor CLI, a local model. The inner rings know one port — launch a
session in a worktree, provision it, name the file it reads its instructions from.

- NO FILE HERE IS NAMED AFTER ONE VENDOR. The system-knowledge file is `AGENTS.md`
  (`port/AgentRuntime.SYSTEM_KNOWLEDGE_FILE`) both in a worktree jagt provisions and in this repository's own root,
  where `CLAUDE.md` is a link to it — one file, never two copies to drift.
- HOW AN AGENT REACHES THE MCP SERVER IS THE ADAPTER'S BUSINESS (direct HTTP with a working-directory header, or a
  stdio bridge for a CLI that can only spawn a server). Nothing above the port knows which.
- THE ONE-SHOT ASSISTANT IS A SEPARATE PORT from the session runtime: an install may run a local model for
  text-to-command and a vendor CLI for the sessions. It is also the only place jagt spends money, so it is metered.

## Assembly validation

At startup, one report of everything wrong — never first-failure, because a half-valid assembly boots and then does
nothing. `startup/StartupCheck` implementations are collected by `startup/StartupValidation`, which throws
`Misconfigured` once with every problem; two of them live at the edge on purpose (`adapter/TtydWebTerminal`,
`adapter/agent/CodexAgentRuntime`), because a driver knows what its own binary needs.

The composition half, and what of it exists:

1. every flow rule names a registered capability — RUN time only today (`FlowEngine` refuses on the click)
2. every status reachable from NEW, and every non-terminal status with a way out — planned
3. every (status × capability × outcome) decided — planned
4. a job's declared capability and watched statuses exist — planned, and `Job` declares neither yet
5. a required port capability present in the SELECTED adapter — only the weak form (a configured type resolves to a
   bean); nothing checks thread resolution, tab titles or an attachable session host
6. one owner per id — built for jobs and for capabilities: a duplicate job id and an equal capability priority both
   refuse to start

Cosmetic capability missing → degrade and say why. A capability a safety property leans on missing → refuse to
start, naming the config key that would make it consistent. Neither is implemented.

## The build, and the modules that are not there yet

ONE Gradle module (`settings.gradle` declares no subprojects). Four test source sets with different meanings:
`test` (hermetic, JUnit-parallel, in `check`), `e2eTest` (git + tmux, real worktrees), `boardTest` (the page in a
real browser), `linuxDriverTest` (Linux binaries, container) — none but `test` is in `check`. Run the staged jar:
`./gradlew build stageJar`, then `java -jar build/libs/jagt-run.jar`.

The rings are folders and `RingsTest` keeps their direction, but the compiler is not the one enforcing it. Two
placements have to be settled before a build graph can be drawn: `config/` (the use cases read its property
records, so it is inner rather than assembly, yet it also holds `FlowWiring`, which IS assembly) and `startup/`
(`StartupCheck` is a contract adapters implement, so that interface is a port; the checks that collect and run them
are not). Then `:core` (task, flow, port) `:usecase` `:surface` `:adapter`, with the existing project staying the
app so `build/libs/jagt-run.jar` does not move.
