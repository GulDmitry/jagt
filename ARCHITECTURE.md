# jagt — architecture

For every agent and every human that opens this repository. Rules live in `AGENTS.md`, situations in
`USE-CASES.md`; THIS file answers only "what kinds of thing does jagt have, and where does a new one go".

STATUS IS PART OF THE MAP. The target is below; the `state` column says what is true today, so nothing here
reads as a description of code that does not exist yet. Do not delete a `planned` row to make the file tidy —
change it when the code changes.

## The law

Anything that runs, tells, decides, or is done to a task is one of the KINDS. A kind is one interface, one
registry, one folder. If something fits no kind, the kind is missing — add a kind, never an exception.

## The kinds

| Kind | Its one job | State |
|---|---|---|
| `task/` | the task record and the file it lives in | built — `TaskState` and everything it is made of |
| `flow/` | which status allows what, and where each outcome leads | built — rules, engine, reports, the projection, and the statuses themselves |
| `capability/` | one thing that can be done to a task | built — one class per verb, none of them naming a status |
| `job/` | work that runs with nobody watching | built (`Job`, `Jobs`, one ticker) — the five job classes still live in `service/` |
| `notify/` | something a human must be told | built (`Notification`, `Notifier`, `Notifications`) — one channel so far, the desktop |
| `surface/` | who is asking: console, board, MCP | built — `surface/console`, `surface/board`, `surface/mcp` |

Drivers are not a kind. They are what a kind needs from the world: an interface the inner rings declare
(a PORT, in `port/`) and an implementation at the edge (an ADAPTER, in `adapter/`). Both exist.

`service/` is what is left: work more than one capability shares — git, the state file, config, processes, tmux,
worktrees. It is named for what it is rather than dissolved into one verb's folder, because a class two
capabilities use belongs to neither.

## The rings — arrows point inward only

```
adapter/   the only place an OS or a vendor is named
    | implements
port/      interfaces the inner rings wrote for their own needs
    | used by
capability/ job/ notify/ surface/        the use cases
    | uses
task/ flow/                              the core: records and rules
```

Two rules, and `RingsTest` asserts both rather than trusting them:

- THE CORE HAS NO FRAMEWORK AND NO PROCESS. Nothing in `task/`, `flow/` or `port/` imports Spring or Lombok, and
  nothing in them imports a ring further out — the machine is assembled from `config/FlowWiring`, so its tests
  build a record and call a function. Two clocks remain in the record itself (a status stamp is *when* it
  happened); threading a clock through every wither would buy a purity the tests do not need.
- THE OPERATING SYSTEM IS NAMED ONLY AT THE EDGE. `osascript`, `notify-send`, `setsid`, install prefixes, a
  Windows shell: all under `adapter/`. Porting is a folder, not a search.

## Nothing below `flow/` knows a status

A capability does its work and reports an OUTCOME. It never names a status and never writes one; the engine
owns the transition, and it is the only writer.

```
TaskCapability:  Outcome run(taskId)                    // no TaskStatus in this file
Outcome:         OK | RELAYED | CONFLICT | PARTIAL | GONE, + the sentence and the stamp
FlowRules:       allow(DEPLOY).from(<statuses>).when(<guard>).on(OK, DEPLOYED).on(CONFLICT, DEPLOY_CONFLICT)
FlowEngine:      rules.allows? -> capability.run -> rules.next(outcome) -> ONE status write
FlowReports:     the second door — a status the task itself reports, same table, same refusals
```

Two doors, one table. PARTIAL is the only outcome that refuses: a half-written shared branch is stamped on the
task first and thrown second, because a sentence in a console nobody scrolled back to is not a record.

## Where a new thing goes

| You want to | Do this |
|---|---|
| add a per-task verb | a folder in `capability/`, plus rules in `flow/FlowRules` if it moves the task |
| add a report or a launch shortcut | one `GlobalCommand` — every surface picks it up (built today) |
| replace a built-in verb | declare a capability with the same id and a higher priority |
| run something before/after a verb | an interceptor on that id — never a new status |
| add unattended work | one `Job`; `Jobs` tickers it and `jobs` lists it — an adapter's own workaround is a job that adapter contributes |
| notify somewhere else | one adapter behind the `Notifier` port; callers learn nothing |
| support another host/tracker/agent/OS | one adapter module; `:app` gains one line |

## The agent system is a ring, not the centre

The coding agent is an adapter: Codex, a vendor CLI, a local model. The inner rings know one port — launch a
session in a worktree, provision it, name the file it reads its instructions from.

- NO FILE IN THIS REPOSITORY IS NAMED AFTER ONE VENDOR. The system-knowledge file is `AGENTS.md`; a vendor that
  needs its own name gets a LINK created by its adapter, never a second copy to drift. Any document jagt writes
  for an agent to read follows the same rule — one that names a vendor has already leaked.
- HOW AN AGENT REACHES THE MCP SERVER IS THE ADAPTER'S BUSINESS (direct HTTP with a working-directory header, or
  a stdio bridge for a CLI that can only spawn a server). Nothing above the port knows which.
- THE ONE-SHOT ASSISTANT IS A SEPARATE PORT from the session runtime: an install may run a local model for
  text-to-command and a vendor CLI for the sessions.

## Assembly validation

At startup, one report of everything wrong — never first-failure, because a half-valid assembly boots and then
does nothing. The config half is BUILT: `startup/StartupCheck` implementations, collected by `StartupValidation`,
refuse the start with every problem at once. The composition half is planned:

1. every flow rule names a status that exists (compiler — the table is code) and a capability that is registered
2. every status is reachable from NEW and every non-terminal status has a way out
3. every (status x capability) has a decided answer, and every allowed pair covers every outcome it can return
4. a job's declared capability is registered and its watched statuses exist
5. a required port capability is present in the SELECTED adapter (auto-review needs thread resolution;
   `tab-per-task` needs tab titles; the web terminal needs an attachable session host)
6. one owner per id — two claimants only with an explicit priority

Cosmetic capability missing -> degrade and say why. A capability a safety property leans on missing -> refuse to
start, naming the config key that would make it consistent.

## Gradle modules along the rings

The dependency rule is not a test: `:core` has no dependency on an adapter, so the wrong import does not
compile.

```
:core  task/ flow/            :port  port/            :usecase  capability/ job/ notify/
:surface  console/ board/ mcp/                        :adapter-*  one per OS or vendor
:app   assembly, config, validation, the fat jar — the only module that sees every adapter
```

Modules come LAST: they confirm a separation that already holds. One jar is still what ships, and the board must
still work with the machine offline.

NOT DONE YET, and named rather than implied. The rings hold as folders and `RingsTest` keeps the direction, but
two placements have to be settled before the build graph can be drawn: `config/` (the use cases read its property
records, so it is inner, not assembly) and `startup/` (its `StartupCheck` is a contract adapters implement, so
that interface is a port while the checks that collect and run them are not). Until then the compiler is not the
one enforcing the rule — the test is.
