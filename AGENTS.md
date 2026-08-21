# jagt — rules

Local orchestration of AI coding-agent CLI sessions across isolated Git worktrees. Java 25 / Spring Boot 4.x,
macOS-first — but every OS- and agent-specific piece sits behind a strategy interface, so a Linux port is new
driver implementations, not a fork.

**This file is `AGENTS.md`.** `CLAUDE.md` is a symlink to it, because no file here is named after one vendor.

| file | holds |
|------|-------|
| `AGENTS.md` (this) | the rules — what you may and may not do |
| [`ARCHITECTURE.md`](ARCHITECTURE.md) | the map — what kinds of thing jagt has, and where a new one goes |
| [`USE-CASES.md`](USE-CASES.md) | the answers — one line per situation |
| [`README.md`](README.md) + `docs/` | what a human installing jagt needs |

Read `ARCHITECTURE.md` before adding a kind of thing this file has no rule for.

## Contents

- [Stack](#stack)
- [Components](#components)
- [Session roles](#session-roles)
- [Control surfaces](#control-surfaces)
- [Whose move it is](#whose-move-it-is)
- [The flow machine](#the-flow-machine)
- [Git safety](#git-safety)
- [One session, many repositories](#one-session-many-repositories)
- [Tasks and state](#tasks-and-state)
- [Review rounds](#review-rounds)
- [Unattended work](#unattended-work)
- [Terminals, sessions and processes](#terminals-sessions-and-processes)
- [Pluggable by design](#pluggable-by-design)
- [Master assistant](#master-assistant)
- [Conventions](#conventions)
- [Testing etiquette](#testing-etiquette)
- [Code quality](#code-quality)
- [Build & run](#build--run)

## Stack

| | |
|---|---|
| Java | 25 |
| Spring Boot | 4.x |
| Build | Gradle, **Groovy DSL only** (wrapper committed). Never Maven, never Kotlin — including `.kts` |
| JSON | Jackson v3 (`tools.jackson.*`, unchecked exceptions); annotations stay `com.fasterxml.jackson.annotation` |
| Board | vanilla HTML/CSS/JS, no build step, no CDN |

## Components

### `orchestrator-backend/`

The Spring Boot app ("The Brain") **and** the Master console itself: state manager, git lock, MCP HTTP server
(`POST /mcp`), watchdog, auto-review scheduler, macOS automation. Run the jar in a real terminal — the process
*is* the Master TUI.

**Outside writes are the sub-agent's job**, via its own MCP: push, merge request, review replies. The one
exception the backend may ever make itself is opening a task's review request over `CodeHost` (`ShipService`),
and only with a host configured.

Outside **reads** have two paths, both opt-in and both needing a token in the environment: a one-shot headless
agent that inherits the human's own MCP, and the read-only `CodeHost` / `Tracker` seams
(`orchestrator.code-host.*`, `orchestrator.tracker.*`). With neither configured, **the backend holds no
credential at all.**

### How an agent reaches the MCP server

Part of the `AgentRuntime` seam, and there are exactly two paths (`adapter/agent/McpEndpoint` documents both):

| path | how | used by |
|------|-----|---------|
| **HTTP** (prefer this) | the CLI is pointed at `orchestrator.mcp-url` and carries `X-Working-Directory: <worktree>` itself | Claude, Qwen |
| **stdio** | the CLI can only *spawn* a server, so `AbstractAgentRuntime.linkStdioProxy` gives it `mcp_client.js`, which POSTs the same header | Codex |

HTTP is verified against a real session and is what took Node out of jagt's requirements. `mcp_client.js`
exists **only** for the stdio path — do not link it for everybody again.

A live session survives a backend restart on the HTTP path (measured 2026-08-17 against a real Claude
session): the server keeps no session id, so the next tool call reaches the new process, and a call that
failed with the backend down does not retire the server for the rest of the session. The stdio proxy's
`ECONNREFUSED` retry is therefore not what a restart depends on.

### `.mcp.json`

Claude Code's project MCP config, **generated per worktree** by `ClaudeAgentRuntime` — not symlinked, because
the header value *is* that worktree's path. The committed root `.mcp.json` is the same server for a dev
session working *on* jagt, with no header: that session is not a task, so the backend treats it as Master.

Other runtimes write their own equivalent (Codex: `.jagt/codex/config.toml`). It is not a universal file.

### Whoever works on jagt reads the same file and reaches the same server

Whatever CLI they run. The root is provisioned for all three exactly as a worktree is, because **a rule only
one vendor loads is a rule half the sessions break.**

| CLI | reads | declares jagt's MCP in |
|-----|-------|------------------------|
| Claude | `AGENTS.md` via the `CLAUDE.md` symlink | `.mcp.json` (HTTP) |
| Codex | `AGENTS.md` natively | `.codex/config.toml` (the stdio bridge) |
| Qwen | `AGENTS.md` via `context.fileName` | `.qwen/settings.json` (HTTP, `trust`) |

None of them carries a worktree header, so every root session is Master.

Two limits are Codex's, not jagt's: it loads a project layer only for a **trusted** project and resolves the
bridge relative to where it was launched (start it at the root), and its approval policy is global, so nothing
here pre-approves it the way `.claude/settings.json` pre-approves jagt's own tools for a Claude session.

**A rule that belongs to this repository goes in `AGENTS.md`, never in a vendor-named local file.**

### `config.json`

User config, gitignored, created by copying the committed `config.json.dist`. Grouped into sections:
`projects`, `viewer`, `dashboard`, `codeReview`, `agent`, `worktree`.

Each section is a small value record (`ConfigService.ConfigFile.*Config`) with `defaults()` + `withX` withers
+ `*OrDefault` accessors. A whole section may be omitted — `ConfigFile`'s accessors coalesce a null section to
its defaults, so callers never null-check.

Never commit user-specific paths. **All config keys are documented in `docs/configuration.md` — keep it in
sync.**

### Orchestrator root

Auto-detected at startup: nearest parent directory containing `config.json.dist` **or** `mcp_client.js`
(`OrchestratorPaths`); overridable via `ORCHESTRATOR_ROOT`.

Two markers on purpose — the bridge is only still here for stdio-only agents, so root detection must not
depend on it. No absolute user paths in the repo.

### What `initialize_task` copies into a worktree

All of it is gitignored, hence absent from a fresh branch checkout, and all of it is best-effort (no-op if
absent).

- **IDE project files** (`copyIdeProjectFiles`), so `ide` opens ready to run and query: run configs from both
  `.run/` (modern) and `.idea/runConfigurations/` (legacy, only "Store as project file" ones), plus the DB
  connections (`.idea/dataSources.xml`, `.idea/dataSources.local.xml`, `.idea/dataSources/` — passwords stay
  in the OS keychain keyed by the source UUID, so they carry over).
- **Gitignored local files** matching the per-project `worktree.copyGlobs`, default `["**/.env"]`
  (`copyLocalFiles`, heavy directories skipped). Run configs reference module `.env` files, keys and SSL
  certificates (`app/.env`, `**/*.pem`) that are gitignored and otherwise missing, so the app would not start. **The patterns are
  config, not hardcoded.**

### `state.json`

SSOT for tasks (gitignored, auto-created), written atomically.

Each task keeps `history` — every status it moved **to**, with when and **who asked**, oldest first, capped at
50 (the file is rewritten on every MCP call). A keep-alive adds nothing (same status = no entry, or four real
transitions drown in hundreds of identical rows), and a task starts its history at the status it was created
with.

The asker (`task/ActionOrigin`) is carried by `service/OriginContext` and stamped in `StateService`, **not
passed down**: a deploy reaches the same code whether it was clicked, typed, said in words or called over MCP,
so every signature in between would grow a parameter it has no use for. Set it at an **entry point** only —
`surface/board/OriginFilter` (both HTTP surfaces at once, so a new endpoint cannot forget), `GrammarDispatch.run`,
`NaturalLanguageDispatch` and `AutoReviewScheduler`. Nesting is honest: console free text is recorded as the
interpretation it became.

**Read "since when in this status" from `TaskState.statusSince()`, never from `lastActiveTimestamp`** — a
keep-alive bumps that one, so an hour-old status would look fresh.

| status | means |
|--------|-------|
| `NEW` | created, the agent has not reported yet |
| `IN_PROGRESS` | the agent is working |
| `REVIEW_PENDING` | handed back to the human |
| `SHIPPING` | a push is in flight |
| `CI_POLLING` | a review round is open |
| `CI_FAILED` | checks red |
| `REVIEWED` | nothing unresolved **and** CI green — but not approved |
| `APPROVED` | a human actually approved the review request |
| `DEPLOY_CONFLICT` | deploy hit a merge conflict; a human resolves it in the deploy worktree |
| `DEPLOYED` | live on the deploy branch |
| `REVERTED` | its deploy was taken back out; branch and commits survive, so the next move is a fix |
| `DONE` | closed |

## Session roles

**Master** is the backend process itself. `MasterShell` owns the screen; `surface/console/GrammarDispatch`
parses the fixed grammar and executes it in-process: no LLM, no MCP round-trip, no tokens, no drift.

There is **no Master Claude session** — the deterministic REPL/TUI replaced it, and `master_prompt.md` went
with it. The only LLM call on the master side is the headless one-shot assistant.

**Sub-agents** are Claude sessions in worktrees named `<taskId>-<projectKey>`, siblings of the base repo.
Their generated `CLAUDE.md` carries full system knowledge (orchestrator root, all projects, active tasks) plus
per-task rules; instructions arrive via `task_context.md`.

## Control surfaces

Two front-ends, **one core**, and the seam is `OperatorUi` (`…surface.ui`, selected by `orchestrator.ui`:
`web` | `tui` | `both`, default web). `OperatorUiRunner` is the only `ApplicationRunner`; a blocking surface
(the TUI, which owns the terminal) starts last, so the board is already serving.

**Adding a surface must not add a second answer to any question the others already answer.**

### One projection answers "what is this task and what can I do with it"

`flow/Move` + `flow/TaskView`, built by `service/TaskViews`. The TUI, `/status` and `/api/tasks` all render
that. `Move.shippable` is also what `ShipService.requireShippable` calls — the dashboard used to advise
independently of the gate, which is exactly how they drifted apart.

`TaskViews.snapshot()` reads the configuration **once** per render and hands back the tasks with the policy
that explains them: the console redraws on every keystroke, and two reads could disagree inside one frame.

### Parity is an invariant, not an aspiration

A capability that exists in one surface only is a bug.

- Per-task verbs come from `Move.actions()`, so a new action appears on both at once — **grouped** there too
  (`TaskAction.Group`: FLOW moves the task on, and closing it counts; TOOL only looks at it or restarts the
  agent). `Move` **sorts** by that group rather than trusting the order somebody appended in, so a new verb
  lands on the right side of the card by declaring its group and nothing else.
- The board renders one row per group and reads which groups exist **off the wire** — a page that knew the
  names would be a second answer.
- Shared text lives in `command/CommandReference` (the grammar) and `command/StateViews` (dashboard + stats),
  so neither surface renders its own version.
- **A hint is `data-tip`, never `title`**: one node placed on hover (`showTip`), because `title` waits and a
  push rebuilds the element it waited on.
- Reports open in a `<dialog>` over the board, never a new page. **Every dialog closes three ways**: Escape,
  its own button, and the dimmed area around it — the click a human makes first. The backdrop close is guarded
  by where the press **started**, so dragging a selection out of a report does not dismiss what is being read.

One deliberate exception: **`quit` is console-only.** Stopping the backend belongs to whoever owns the process
(Ctrl-C / kill), not to a browser button, and nothing is lost since agents live in tmux. A shutdown endpoint
was built and removed — do not add one back.

### "What commands exist" has exactly two answers, and both are declarations

| the verb | is | executed by |
|---|---|---|
| one a task owns | a `flow/TaskAction` row, gated by `Move` | `CommandService` |
| one no task owns | a `command/GlobalCommand` bean (`command/*`, collected by `GlobalCommands`) — id, hint, usage, whether its answer is a report, whether it is console-only | itself |

`CommandReference` **renders both** — `help`'s text and the palette's verb list — so a hint is written once.
`GrammarDispatch` **looks a typed word up** in the two instead of switching on it. `GET /api/commands/{id}`
serves any report, so declaring another one needs no endpoint, no console arm and no button in the page (the
board *builds* its report buttons from that list).

This is what parity failed on before (2026-08-19): the per-task verbs always had this shape, while
`do`/`resume`/`stats`/`activity`/`help` were hand-written in six places each.

Three deliberate limits: that endpoint refuses anything that is not a report (a GET must not be able to start
a task); a console-only command is filtered out of what the board is told at all; and tier 2 stays narrower on
purpose — a prose request cannot ask for a dialog, so `NaturalLanguageDispatch` names the two launches itself
and offers no report.

### Two-tier dispatch

**Tier 1** is the grammar (a typed command or a board button) and it stays LLM-free.

**Tier 2** is `service/NaturalLanguageDispatch`: free text — an unknown console line, or the board's ⌘K
palette → `POST /api/interpret` — goes to a model that only **proposes** one grammar command. The dispatcher
validates that the task exists and the verb is real, then executes through `CommandService`, so **tier 2 can
never do more than a button**.

The call is deliberately stripped (`--strict-mcp-config --mcp-config '{"mcpServers":{}}'`, no
`--setting-sources`): text→command needs no tools, and a loaded MCP server would be paid for in context.

It answers with the interpretation **first** ("understood as `ship a1` — …"), and a single unknown word is a
typo, not a request — it never reaches the model.

### A renamed verb keeps its old spelling, and advertises only the new one

`sweep`, typed as `review` since 2026-08-18. **One map owns it** — `TaskAction.RENAMED`, read through
`byRetiredVerb` — and every surface where a human types has to consult it: the console's grammar, the palette
(`CommandReference.Verb.aliases`, which the page matches and offers nowhere) and a tier-2 proposal that echoed
the word.

Two things it deliberately is not: `byId` stays **strict**, because that one answers a URL segment and a
retired verb is not a wire id; and the lookup resolves retired spellings **only**, never a current id — the
grammar's verb set is the switch, so `diff …` keeps reaching the model as free text.

`CommandReference` names just the current verb: two spellings in `help` are two answers to one question.

### How an action is executed

`service/CommandService` validates against `Move` first, so a stale board tab is refused with a sentence, not
with a git error three layers down. `service/TaskLauncher` is how a task is started. The console parses a
command line, the controller parses JSON; **neither owns rules.**

The sentence stays the whole answer for a human. A refusal a caller must **act** on also carries a
`flow/Refusal.Code`, and that enum grows **only** when something branches on the new value — a reason nobody
handles differently keeps throwing plain.

### There is no tools facade any more

Do not bring one back. `OrchestratorTools` grew to 871 lines and eleven collaborators, and every attempt to
thin it *added* one, because a delegating aggregate keeps what it does not shed. It was dissolved
(2026-08-14).

Each MCP tool group declares its own tools (`surface/mcp/McpTools` + `surface/mcp/McpToolRegistry`,
implementations under `surface/mcp/tools`). Every other caller takes the small service it actually uses:
`AgentSessions` (tmux window, focus, kill, relay), `TaskProvisioning` + `WorktreeSetup` + `SubAgentBriefing`
(creation), `AgentStatusReports`, `IdeLauncher`, `DeployService` (the only shared-branch writes),
`TaskRetirement`, `TaskResume`. Per-task verbs are a class each under `capability/`, reached through the flow
engine. `surface/mcp/CallerScope` owns the X-Working-Directory rule for all of them.

### No limit on concurrent tasks

A decision, not an omission. A cap (`agent.maxConcurrentTasks` + `TaskAdmission`) was built and then **removed
on the owner's instruction**: jagt runs on other people's machines, one of which has 100 GB of RAM, so a
number picked here is wrong for almost everyone, and refusing a `do` on that basis is jagt deciding something
it cannot know. Whoever wants a bound has the machine's own tools for it.

Do not reintroduce a cap, a queue, or a "slots" indicator.

### The board listens on loopback

`server.address: 127.0.0.1`, because it asks for no password and can deploy, close a task, start an agent and
— with the web terminal on — hand out a writable shell. Widening it is a config line the human writes
themselves.

### The board is two rings too

Vanilla HTML/CSS/JS under `src/main/resources/static` — **no build step, no CDN, no external asset of any
kind** (it must work with the machine offline and stay inside the one jar).

Native ES modules in two rings, the same rule as the backend's: `core/` answers a question without owning a
node on the page, `ui/` owns the nodes it renders, `app.js` only wires them — and a module never reaches for
another's element (it takes a callback at wiring time).

A card carries `data-action`, never a closure: `ui/render` holds the one delegated listener on the grid, so a
card rebuilt under the pointer cannot act for the task it used to describe.

`ARCHITECTURE.md` has the file-by-file map.

### Neither surface polls

`StateService.onChange` is the one event both use: `TaskEventStream` forwards it as SSE, and `MasterShell` sets
a dirty **flag** its render loop consumes (Lanterna's screen belongs to the UI thread; the listener runs on
whichever thread served the agent's MCP call — never paint from there).

The SSE event carries **no payload** on purpose: a payload would be a second serialization that could disagree
with `/api/tasks`. The periodic tick survives in both only for the relative "ACTIVE" clock.

### A desktop banner clicks through to the task it is about

`DesktopNotifier` → `UserNotifier.notify(…, link)`. A banner that only **names** a task leaves the human to
find it, which on macOS means finding the browser tab first.

The link is the board narrowed to that task by the filter the page already has — not a second way to address a
card — and it is omitted when there is no board (`orchestrator.ui=tui`) or no task, since a click onto a dead
page is worse than one that does nothing.

It exists at all only because macOS banners cannot carry an action without `terminal-notifier` (`-open`),
which `MacNotifier` already prefers for its own reasons. osascript and `notify-send` both drop the link, and
**no caller may depend on it.**

## Whose move it is

### A blocked session is on the dashboard, whatever blocked it

The owner's rule, 2026-08-19. It has two halves, because a stopped agent may or may not manage to say so.

**The agent's own half** is `outcome=question` *before* it puts any question to a human — rule 1 of
`sub-agent-context.md`, and the `update_agent_status` tool description says it too (a worktree is briefed once,
while a tool description reaches every session). `AgentReport.QUESTION` flips `Move.owner` to YOU from whatever
status it kept, `DashboardLine` reads NEEDS INPUT, and `AgentStatusReports` pings once, on the transition
**into** asking.

**The half no prompt can promise** is the agent that never got the chance — a token limit, a crash. So
`WatchdogService` **stamps** what it probes (`TaskState.silentSince`: stale MCP plus a quiet tmux window)
instead of only sending a ping a human dismisses, and the same owner flip plus a NEEDS YOU line happen with
the agent saying nothing.

**What the watchdog cannot see is a live session sitting at a prompt**, and that is measured, not assumed: a
Claude window waiting on a question repaints every 10–30s (2026-08-20), so the window half of the probe stays
warm forever and no stamp is ever written. An agent asking anything — its own question tool, a plan to
approve, a permission prompt — is therefore reachable **only** through its own `outcome=question` report.

Three things hold it up:

- Every status whose `Move.ownerOf` is AGENT is watched by the watchdog, pinned in `WatchdogServiceTest`. A
  status the agent owns and nothing watches is a session that waits forever.
- The stamp is written only on the **transition** in or out of silence, because both surfaces repaint on a
  state write and that job runs every minute.
- **Any** report clears it (`withStatus`), so a task that answers stops asking.

Neither surface needed a new field for it — owner, hint and detail already carry it, which is what parity
means here.

### `Phase` / `Owner` are a projection, never a second state machine

`TaskStatus` stays the SSOT; neither is persisted. Eleven statuses collapse into six phases because four of
them read as the one word "review".

**Liveness is deliberately not an input to the projection** (it would cost a tmux probe per task per render).
A task stuck at SHIPPING is therefore offered SHIP, and the gate refuses at execution time if its agent is
alive.

### `Owner.YOU` means an action of theirs exists

A status alone cannot always say so. The board's badge, its "action required" count and its own-move filter
all read the tier **below**, which is NONE exactly when the owner is not YOU — so a card that asks for a human
who has nothing to do teaches them to ignore all three.

Two cells are decided by more than the status (`Move.ownerOf`):

- A REVIEW_PENDING round that changed nothing and drafted no reply waits on the **reviewer** — the only move
  left is a ship jagt itself advises against.
- A round whose expected poll has **stopped** waits on the human. That is `AutoReviewWatch.stopped()`, not "is
  not being polled": an install that polls nothing at all says so once per surface, and flipping every card in
  it to "action required" would be the same unread badge in the other direction.

The projection reads it off the very watch the card renders, so the badge and the countdown cannot disagree. A
caller that only wants the sentence keeps the four-argument `forTask`.

### Work handed in and waiting for a reviewer is not an action required

The owner's rule, 2026-08-20. REVIEWED means "nothing unresolved, checks green, **not** approved" — the status
*before* an approval. Its owner is the **code host**, nothing is highlighted on the card, and no desktop ping
goes out. `deploy` stays in the action list for an install that needs no approval, because gating it was
already decided against.

Three things follow, none optional:

- **APPROVED is the one the human is tapped for.** `AgentStatusReports.ping` asks `Move` whose move it is and
  sends nothing unless the answer is YOU — a second list of statuses worth interrupting for would drift from
  the badge.
- **A REVIEWED round is still polled**, as is every other status: `AutoReviewCadence.polls` asks only whether a
  request is open, because an approval arrives after the round came back clean.
- **Where nothing is polling for the approval, the read is what the card highlights.** That is why
  `Move.forTask` takes the `AutoReviewWatch` and not a flag off it: "the poll this round was promised has
  stopped" and "nothing is polling at all" are different questions.

### An action that can wait is not an interruption

The owner's rule, 2026-08-21. The human's own turn has two tiers, and `flow/Attention` is the one value that
says which:

| tier | means | gets |
|------|-------|------|
| `REQUIRED` | a session that stopped or asked, a round back from review, a red run, a conflict, a round nothing will read again | the header count, the own-move filter, the alarm edge |
| `OPTIONAL` | a good state whose next move is theirs whenever — today an approval that landed, and a revert they made themselves | a card-level chip in the flat chip colour, nothing else |
| `NONE` | exactly when the owner is not YOU | nothing |

Pinned over every status in `MoveTest`, because a card counted in "N need your action" while its badge says
the move can wait is the drift that makes the badge worth nothing.

The words come from the enum (`attentionLabel` on the wire, exactly as a status ships its label), so the
console prints the same two.

**The ping still reads the owner rather than the tier** — an approval landing is an event worth a banner even
though it can wait.

### Neither is work that is already live

The owner's rule, 2026-08-21. DEPLOYED's owner is **nobody**, exactly as DONE's is. Closing the task is
housekeeping a human does when they feel like it — `done` stays the highlighted move and nothing else is left.
A badge there sat next to a stalled session wearing the same colour and the same word, which is precisely how
a human is taught that "action required" means nothing.

A question asked from DEPLOYED still flips it: a stopped session **is** their move.

### A question outranks the status it was asked from

In all four places that read a round: `Move.ownerOf` (the wait is the human's), `Move.primaryOf` (FOCUS — the
status alone would highlight a verb that *acts*, and a SHIP on a round the agent said it cannot finish is the
worst button on the board), `Move.hint` and `DashboardLine`.

Reachable from **every** status, because an agent may ask without moving its task — and the statuses a
question is not expected from are exactly the ones nothing else flips. A closed task's leftover message is the
one exception.

### Whether the request is approved is shown, not inferred from the status

`TaskState.approved` is stamped by the same read that stamps the pipeline (`ReviewSweepService.record`, one
write for both, null until a read has said). Both surfaces show it beside the request the moment it opens —
the board as a dot next to the MR link (filled when approved, an empty ring while it waits), the console as a
prefix on the request line.

A status cannot answer this: the wait starts when the request opens, and only one status is ever the approval
itself. A new round drops it with the pipeline, since neither describes the request state that follows.

### A status says itself in words, once

`TaskStatus.label()` is the spelling both surfaces render (`out for review`, `not shipped`, `not approved`),
while the enum name stays the wire value, what `state.json` carries and what the board hangs in the chip's
tooltip.

It names a **state** and never a next move — the highlighted action already gives that, and a status that
advised too would be the third copy of one sentence.

The board binds the age **inside** that chip, because `CI_POLLING · 18m · sng` reads as three unrelated items
with the middle one anybody's guess. The console prints the same pair in the same order.

### Position does not carry state

This is what makes the board readable. A phase that owns a column has to **move** the card it describes, and
re-finding it is the cost a human pays for the arrangement; an order that follows activity moves cards on an
agent's keep-alive, which is motion nobody asked for.

So `TaskViews` orders by **alias** (numerically — plain text puts p10 before p2) for both surfaces, the board
lays one card per grid cell, and a status change repaints the chip in place. A phase is a **count** in one line
above the grid, zeros included, so that line cannot move either.

The board offers no sort control. What it offers is **narrowing** — a filter over alias, id and title, plus
needs-my-action — because a filter is an explicit act with a visible control, while automatic reordering is
what costs a human the position they had learnt.

An alias is the lowest free number, so closing one task and starting another can shift the grid by one place.
That is motion from the human's **own** action, which is the only motion this design allows.

### Three clocks, three questions, and none of them answers another's

| clock | answers | shown |
|-------|---------|-------|
| `statusSince` | time in **this** status — restarts on every real transition, including a respawned agent re-reporting itself out of REVERTED | inside the status chip |
| `lastActiveTimestamp` | liveness for the watchdog — any MCP call, keep-alives included | a console column and a tooltip line, **never** a card row |
| `TaskState.requestOpenedAt` | the review's own age | the `MR <age>` chip, which is also the link |

`statusSince` cannot answer "how long has this been waiting". `lastActiveTimestamp` on a status the agent does
not own says only that nothing has happened, which the status already said.

`requestOpenedAt` is stamped **twice, from two different clocks**: first by jagt's own the moment a request new
to the task is linked (`TaskState.relinked`) — a **floor**, so the card has an age from the first second, and
`ship`, an agent reporting the url and `resume` all reach that method. Then by the request's **own** creation
time, which replaces it on every review read (`ReviewFacts.openedAt`) and is the value that survives — asked of
both readers, the host's `created_at` and the model read's `openedAt`, because `resume` adopts requests that are
weeks old. A read that cannot say passes 0 and `withRequestOpenedAt` keeps what is there, so **a correction can
never blank an age.**

Not to be confused with `mrCreatedAt`, the round window `AutoReviewCadence` measures. That window is **per
round**, and a round begins on every **entry** into CI_POLLING — whether `ship` put it there or the agent
reported it — while a repeated CI_POLLING keeps it. Measured from the first request ever linked instead, a
task sent back out for review would be past its window on arrival.

On the board the request age is also the **link** to the request, and the task number the link to the ticket —
one element per fact, so no row spends a line naming what it points at. Several repositories mean several
requests against one stamp, so those links are named by project and ageless.

### The embedded terminal is a rendering of `focus`, never a second verb

With `orchestrator.web-terminal.enabled`, a Focus click on the board also opens the task's tmux session in a
`<dialog>`. `adapter/TtydWebTerminal` serves **one ttyd per tmux session** (not per task — a task is a window
inside one), and `POST /api/tasks/{id}/terminal` hands back its address, `null` meaning none is configured.

It selects no window and executes nothing; the action itself still goes through `CommandService`, so the
console keeps raising the native viewer and the card grows no button outside `Move.actions()`.

Four things it owes, none optional:

- **The terminal is writable**, because a view you cannot answer the agent in is pointless — and a writable
  terminal is a **shell**, so `--check-origin` is what makes the served page the only origin that may open a
  socket. A websocket handshake is exempt from same-origin rules, so without it any page the human visits can
  drive the agents' session over loopback. **The bind address is not that defence and never was.**
- `--exit-no-conn` ends the server with the last viewer, so a `done` that kills a tmux session cannot leave a
  ttyd behind and no port leaks.
- The port is the first **free** one from `web-terminal.port`, so a server orphaned by a `kill -9` moves the
  next one along instead of killing the feature.
- The frame is **unloaded** on close, since tmux sizes every window to its smallest attached client, including
  one nobody is looking at.

ttyd stays **one class**, not a sixth seam — a second web terminal is an interface extraction, and nothing
outside it names ttyd.

## The flow machine

`flow/FlowRules` is the whole life of a task in one file: which statuses allow which action (the guard reads
`flow/Facts` — an open request, and a liveness probe the projection passes as "no" because it costs a process
spawn per row), and what each outcome of that action leads to.

**Door one** is `flow/FlowEngine.run`: check the rules, run the `capability/TaskCapability` registered for the
action, write the status the table gives for its `flow/Outcome`.

**Door two** is `flow/FlowReports`: a status the task itself reports — its agent over MCP, or a round jagt read
for it. Refused unless `FlowRules.refusedReport` allows it, **and it owns the reason**, which is what the agent
acts on. That is what stops a task talking itself onto a shared branch, out of one, or closed.

A status a **human** owns is not refused but **held**: `FlowRules.reported` keeps a REVERTED task where it is
and records the line. An agent's protocol is to keep saying what it is doing, so a status it cannot report is a
session whose every call errors — while a status that *follows* it took the revert off the record and laundered
the CI_POLLING guard through the `IN_PROGRESS` it had just claimed.

**Nothing below `flow/` names a status.** A capability does the work and reports OK / RELAYED / CONFLICT /
PARTIAL / GONE plus the sentence and the stamp, so the same work can be reached from several statuses without
every doer learning the machine.

`withStatus` therefore appears in `flow/` and in the record that implements it, **nowhere else** — that is
greppable, and it is the invariant.

PARTIAL is the one outcome that **refuses**: stamped on the task first and thrown second, because a shared
branch holding half a change must be recorded, not merely complained about.

The table stays Java rather than config: every status and action in it is checked by the compiler.

## Git safety

### The only writes to a shared branch

`deploy` (task branch → `deployBranch`, via `GitService.mergeIntoAndPush`) and its undo `revert`
(`revertMergeAndPush`: reverts the merge commit deploy recorded — **adds** a commit, never rewrites history,
never force-pushes). Both are Master-only and both go through `deployTarget`, so they share one deployBranch
guard.

`ship` creates or updates a merge **request** only. Never merges.

`revert` refuses rather than guess in every ambiguous case: no recorded merge commit (a deploy from before
`deployCommit` existed — the human gets the by-hand `git revert -m 1` recipe, jagt will **not** search the
log), the commit is not on the branch, it was already reverted, or the revert conflicts (aborted and cleaned
up; unlike a deploy conflict there is no half-state worth keeping).

### The base branch is read-only

`baseBranch` is what tasks are cut from, and nothing ever pushes or merges to it. That holds for a per-task
base too (`do <ticket> from <branch>`, persisted as `TaskState.baseBranch`): it moves what the worktree is cut
from and what the merge request **targets**, never what anything merges into.

`deploy` stays on `deployBranch` whatever a task's base is. Read the effective base through
`TaskState.baseBranchOr(project.baseBranch())`, so the worktree, the MR target and `ide … diff` cannot drift
apart. `deployTask` **refuses** when `deployBranch` equals `baseBranch`.

Sub-agents are forbidden by prompt rule from pushing or merging anywhere but their own task branch. A worktree
branch is cut from `origin/<baseBranch>` and inherits it as upstream, so `GitService.detachUpstream` unsets it
right after creation — a bare `git push` then errors ("no upstream") instead of pushing the task branch
straight into the release branch.

`GitService.pushBranch` pushes **one** task branch with an explicit both-sided refspec: never `--force`, never
`-u` (an upstream is the trap `detachUpstream` removes).

### What a reviewer said is not a gate on `deploy`

The owner's call, 2026-08-18. `Move.deployable` asks only whether a request is open — plus DEPLOY_CONFLICT,
which is finished by deploying again — because deploy merges the task **branch**, and git's only precondition
is commits on it. Gating the button on REVIEWED/APPROVED meant a human looking at a REVIEW_PENDING card could
not land a request they had decided to land.

What stays excluded is what could only race or refuse:

| status | why |
|--------|-----|
| NEW | nothing on the branch |
| SHIPPING | a push in flight |
| IN_PROGRESS | an agent committing **into** the branch this would merge |
| REVERTED | a revert adds a commit, so the branch holds nothing the deploy branch lacks |
| DONE | closed |

### The confirm names the writes and nothing else

The board names the writes it is asking for before it makes them — `deploy` and `revert` alike, one
`project → branch` line per repository, read from `TaskView.RepoView.deployBranch`, because "the deploy branch"
is not something a human can check. `revert` names its **scope** too: only the last deploy comes out.

**That is all either confirm says.** The deploy one advises nothing about the round (the owner's instruction,
2026-08-21): it used to warn that a REVIEW_PENDING round was never shipped and that a `review_replies.md` was
still in the worktree, and both were wrong often enough to train a human to click the dialog away — which costs
the branch lines the only reader they had.

A deploy is the human's to make at any moment; jagt states the writes and gets out of the way. **Do not add a
warning, a badge or a gate to that question.**

### No bulk branch cleanup

A decision, not an omission. `prune [all]` (a cross-project sweep of local branches merged into `deployBranch`)
was built and then **removed on the owner's instruction**: branch cleanup belongs to the one task it concerns,
and a human who wants a branch gone has git.

Do not reintroduce a prune verb, a "merged branches" report, or a board button for either.

### A commit carries the task's work, never jagt's own plumbing

`commitAll` stages everything and then unstages what jagt writes into a worktree regardless of the checkout
(`WorktreeFiles.GENERATED`).

`info/exclude` answers only for **untracked** files, so a project that versions one of those names — jagt
versions `.mcp.json` — used to ship the copy written for that worktree, absolute caller path and all, and every
other clone then read a header pointing at somebody else's directory.

The names jagt **refuses to overwrite** are deliberately not on that list: a modified `AGENTS.md` is the agent's
work. The price is that jagt's own generated files can only be changed in this repository by a human commit.

### A branch the base repository still holds is freed, not refused

`GitService.freeCheckout`. Git allows one checkout per branch, nobody works in the base repository, and a task
blocked on a checkout nobody remembers making is worse than a WARN naming what it was on.

Four things that are not incidental:

- It detaches the repository **in place** — no other ref, so the files an editor has open do not change under
  it, and a per-task base with no local branch is no obstacle.
- It runs **inside** the recreate/resume arms, never before the strategy switch, because a refusal must leave
  the repository where it was.
- The detach is **undone** when what it was freed for does not land, in `createWorktree` and again in
  `TaskProvisioning`'s unwind (a resumed branch survives, so there is something to go back to).
- It ignores **untracked** files, since only tracked changes are carried.

Two cases stay refusals: tracked changes in that checkout, and a branch held by **another** worktree.

### `ship` is deterministic when a `CodeHost` owns the repository

`ShipService` commits the worktree, pushes the task branch and opens or updates the review request in-process
(`GitService.commitAll`/`pushBranch` + `CodeHost.createOrUpdateMergeRequest`), then sets CI_POLLING with the
link. **No model on that path**, so SHIPPING is no longer a state a task can hang in.

Two things stay deliberate: a review-round commit message is **mechanical** (`<task> address review comments`)
because the backend cannot describe what the agent fixed; and posting the drafted `review_replies.md` is still
relayed to the agent — a reply needs the thread it answers, which `ReviewFacts` does not carry — but as a
**follow-up**, never on the critical path.

With no host configured the old prose relay is kept verbatim: an unconfigured setup must behave as it always
did.

### All git ops under a per-repository lock

`GitService` holds a `ReentrantLock` per repository: `index.lock` races are per-repo, and a slow fetch in one
project must not block another.

### No git hooks, ever

Never propose, add, or rely on any git hook anywhere. Enforce invariants in code and prompts.

## One session, many repositories

What multiplies is **worktrees**, never agents. A task holds a list (`task/TaskRepo`, `repos.get(0)` = where
the session runs) and every per-repo step iterates it: creation cuts a worktree each
(`TaskProvisioning.resolveRepos` validates **all** of them before cutting **any**, and a failure part way
unwinds the ones already cut), `ship` commits/pushes/opens a request per repository against that repository's
own base branch, `done` deletes every worktree — the siblings hold checkouts and copied secrets nothing else
would remove.

A task's **own** repositories are one scope, not several: `StateService.findByWorktree` answers from any of
them, so a multi-repo task stays one caller however many worktrees it holds. Narrowing that back to the primary
worktree silently breaks every tool the agent calls from a sibling repo.

Three rules that are not obvious from the loop:

**The review round is merged**, and it answers as the least finished repository
(`ReviewSweepService.merged`): approved only when all are, the pipeline the single **worst** one — never a
concatenation, which reads as "success" to the caller's own check — and each comment prefixed with the
repository it came from. Reading only the session's request would let a green half advance the whole task.

**`ship` is all-or-nothing about hosting**: one repository without a `CodeHost` sends the **whole** task down
the prose relay, because half pushed by jagt and half asked of the agent is a state nobody can describe.

**`deploy` lands in order and stops at the first conflict**, and the sentence names both sides — what is live
on the deploy branch and what is not. A shared branch cannot be written atomically whatever jagt does, so the
honest half-state beats a dry run that only makes the same failure rarer at twice the merges.

- Every repository is checked deployable before the **first** push.
- The half-state is read from **where the sequence stopped**, never from the recorded merge commits — those
  outlive the round that made them, so after a second ship every repository would read as live.
- Sibling repositories derive the same deploy worktree path (`<taskId>-deploy`, next to the repository), so the
  directory alone never decides anything: `GitService.hasDeployWorktree` asks git who cut it,
  `mergeIntoAndPush` **refuses** to finish a worktree another repository owns (it would push that repository's
  work to this one's remote), and only a task handed back at DEPLOY_CONFLICT resumes at one.
- **A directory that is no checkout is not an obstacle and never a sibling's conflict** (2026-08-21, and it
  cost a whole morning of `deploy` refusing the same task): `ide` on a deploy worktree leaves the editor
  holding it, so after the worktree is removed the editor writes its project files back into the empty
  directory. That residue is **deleted** and the deploy goes on (`clearEditorResidue`); a path holding anything
  else is left untouched and named (`StaleDeployPathException`).
- The blocked sentence follows the same rule: **a repeat is advised only when something landed**, because that
  is the case where the sibling holding the path has just released it. With nothing landed the human gets the
  obstacle instead of an instruction that loops.
- **Nothing to deploy is not a failure** (`GitService.NothingToDeployException`): a repository whose branch adds
  nothing — never touched by the change, or already on the branch — is passed over and named, which is also
  what makes starting the sequence over harmless when no worktree answers.
- A stop for any **other** reason leaves the status alone (there is nothing to resolve in a worktree) but still
  names what landed.

`revert` walks back the other way: reverse order, only the repositories that have a merge commit, each one
**forgetting** it as it comes out, so a repeat touches only what is still live — and REVERTED is set only when
everything that landed is out.

Both half-states are **stamped on the task**, not just thrown: a sentence in a console nobody scrolled back to
is not a record of a shared branch holding half a change.

## Tasks and state

### A task is created with its item's own facts or not at all

The owner's rule, 2026-08-21. `TaskLauncher` reads the reference on **every** launch. The fast path (a bare key
plus an explicit project skipped the read, and a background `TicketTitleBackfill` filled the title in later) is
gone, and so is the backfill: a card being worked on whose ticket link is missing cannot be repaired
afterwards, because no later read can tell an item that **has** no link from one that was never reached.

`TicketFacts.usable()` is the gate — a key, a title **and** a link, all three, because an item that exists has
all three and a card missing one is a card nobody can tell from the next.

An answer that fails it is asked **again** (`TicketReader`: 5 attempts, 2s apart, bounded by two minutes so a
launch a human is waiting on cannot hang on five CLI timeouts), because a model that never found its tracker
tool reports precisely the `exists=false` a deleted item reports. **Only a model's negative is re-asked**: a
configured `Tracker`'s "no such item" is a fact, and re-reading it through a paid call is the fallback that
rule already forbids.

A bare key whose read answers a **different** key is refused as well, naming both.

The price is deliberate: every `do` now pays for one ticket read — free with a tracker configured, one metered
model call without one.

**A source with no summary of its own is not an exception to the gate**, it is the read's job: the prompt has
the reader write a title of its own from the description, since a reader that reached the item at all can name
it in a few words. Nothing invents a URL — that one is read or the launch refuses.

### `state.json` writes

Atomic (temp file + `Files.move` ATOMIC_MOVE) in `StateService`. Atomicity covers a **torn** file, not a **bad**
one, so every write also copies the previous version to `state.json.bak`, and a read that cannot parse the
primary recovers from that backup (moving the bad file to `state.json.corrupt`).

With no usable backup it **throws**: starting with an empty task list over an existing state file would destroy
the human's data on the next write. **Never make that path fail soft.**

### Sub-agents can only act on their own task

X-Working-Directory scoping is enforced in `surface/mcp/CallerScope`, and its wiring into each tool is what
`McpToolScopeTest` pins — the rule was real for three tools and **missing from four** until 2026-08-14, so a
new tool taking a taskId gets a row in that test, not a promise.

`initialize_task` / `remove_task` / `deploy_task` / `revert_task` are Master-only. Task ids are validated
(`[A-Za-z0-9][A-Za-z0-9_-]*`) — they become branch, directory and tmux names.

Every MCP tool call from a registered worktree bumps `lastActiveTimestamp` (the watchdog keep-alive).

### The MCP transport must never emit non-JSON-RPC bytes

Malformed JSON → `-32700` from the controller. HTTP errors → a synthesized JSON-RPC error in `mcp_client.js`
(never forward Spring error pages).

The proxy retries **only** `ECONNREFUSED` (the request was never sent) — other failures may have executed a
non-idempotent tool.

### Watchdog scope is deliberate

`WatchdogService.watches` alerts only for statuses where the **agent** is expected to be working: NEW,
IN_PROGRESS, SHIPPING. Every other status idles by design (CI_POLLING waits on the code host,
REVIEW_PENDING/REVIEWED/APPROVED/DEPLOY_CONFLICT on the human), and watching those turns the alert into noise.

### `WorktreeOrphanScanner` only ever looks

Worktree directories no task owns can hold uncommitted work **and** copies of secrets
(`worktree.copyGlobs`), so it WARNs one line each at startup, plus one desktop ping, and **deletes nothing**.

No surface offers it — the board dialog and `GET /orphans` were removed on the owner's instruction
(2026-08-18), and the console never had a verb for it: housekeeping is not something a human acts on
mid-flight, and the board is dense enough. **Do not add either back.**

It is a job with no interval (once, as soon as the application is up) and it catches nothing itself: a throwing
run is booked against that job by `Jobs`, because a diagnostic must never stop the backend from starting.

### What is missing is said at startup, not at the click that needed it

`startup/StartupValidation` asks every `StartupCheck` before the operator surfaces open and refuses the start
with **all** problems at once (`Misconfigured`, printed by `StartupFailure`) — a human fixes one list instead of
one item per restart, and each line names the key that fixes it.

A check lives **next to** the part it answers for, so it exists only when that part was selected and nothing
branches on which terminal, agent or host is configured (`CliEditorDriver`, the kitty driver,
`TtydWebTerminal`, `LibNotifyNotifier`, `CodexAgentRuntime`). What no implementation can answer for — a `type`
that selects nothing, the human's `config.json`, jagt's own paths, git and tmux — is a check in `startup`.

Two limits are decisions, not gaps: **nothing reaches the network** (presence, never validity — a wrong token
is the first read's answer, and a laptop offline must still start), and nothing asks a remote about a branch
(that is a fetch per project on every start).

`orchestrator.startup-checks=false` belongs to **test harnesses only** — what the checks ask about is the
human's machine, and a runner is not one, so every suite and smoke script that boots the app passes it exactly
as it passes `open-warp-window=false`.

### Tomcat's "Error setting socket options"

A `SocketException` at `setSoLinger` is a connection the peer aborted between `accept()` and configuring it: a
browser pre-connect, the losing half of a Node client's IPv6/IPv4 race to `localhost`, a `curl` probe.
`SO_LINGER` is simply the first unguarded setsockopt, and Tomcat gives no knob (`AbstractProtocol` sets
`connectionLinger` in its constructor).

`surface/board/AbortedConnectionFilter` drops that one event and nothing else. Do **not** "fix" it by silencing
`NioEndpoint`, which also hides real socket errors.

## Review rounds

### Code review is never fully automated

The auto-review poll (`AutoReviewScheduler` → `ReviewSweepService`) only **reads and drafts**. An approval may
advance status, but comments are merely **relayed** to the agent, which fixes locally and writes its intended
answers to `review_replies.md`.

Nothing is pushed or posted without an explicit human `ship`. The loop never ships, deploys, pushes or posts on
its own. Every round hands the human two artifacts to inspect via `ide <alias>`: the local diff and the drafted
replies.

**Do not erode this: the human-in-the-loop gate lives in the outcome, not in who triggered the sweep.**

### A review round is a judgement, not a work order

Relay a bare list of comments and the agent implements all of them — including the ones wrong about the
architecture, which the reviewer could not see from the diff — and the human then reads agreement into code
that was only obedient.

`ReviewSweepService.brief` therefore opens with the three routes per comment: fix, change **nothing** and say
why, or ask via `outcome=question` before guessing. `sub-agent-context.md` carries the same stance for the task
itself.

A question **ends** the round (REVIEW_PENDING, `outcome=question`) rather than parking in CI_POLLING, because
the wait is the human's and the card has to say so. What keeps the agent from being re-briefed on the comments
it was told to hold is `AgentSessions.relayIfChanged`, not the status it left: a relay **nudges** the session,
so a brief the file already holds is an interruption to re-decide answered comments.

Deliberately **not** extended to jagt's orchestration steps: a commit or ship instruction **is** the human's
approval and is executed as given.

### A round reports its outcome as a field, not as a turn of phrase

All three outcomes end at REVIEW_PENDING and the human is advised from it, so `update_agent_status` takes
`outcome` (`question` | `no_changes` | `progress`) and `reviewRequestUrl`. The two structural facts stop being
scraped out of prose: the marker `flow/AgentReport` parses is written by **jagt**
(`AgentStatusReports.stated`), and the message is the human's sentence.

The prefix an agent typed itself is still read, as the **fallback**: a worktree keeps the brief it was created
with. `AgentReport` stays the one parser of the vocabulary (`Move` and `DashboardLine` both read it, so they
cannot disagree), and `Move` is total over (status × report).

**One of the three is checked rather than believed**: `no_changes` over a worktree holding uncommitted work is
recorded as a round **with** a diff (`WorktreeChanges` — one `git status` per report, never per render). That
claim is the one that suppresses the ship advice, and a hidden diff is what a human would then never read. A
question is not checkable, and no schema makes it so.

Advising SHIP for a no-change round is a **loop**: the ship commits nothing and starts another round on the
same unresolved threads. So NO_CHANGES highlights nothing and says the open threads are the reviewer's move.

### A reply does not resolve a thread

The sweep relays every **unresolved** one (`resolvable && !resolved`), so a comment the agent pushed back on
comes back every round forever.

The agent therefore resolves — at **ship** time, with its own MCP, never jagt's `CodeHost`
(`ShipService.repliesStep`) — **only** the threads whose code it actually changed. A thread it disagreed with
or asked about stays unresolved: that disagreement is the reviewer's to settle, and resolving it would read as
agreement.

During the round the agent posts nothing at all — `review_replies.md` holds **drafts** until the human ships.

### The checks are read where the comments are, and shown without being asked for

A sweep already pulls the review round, so it stamps what the host said about the pipeline onto the task
(`TaskState.pipelineStatus`, the host's **own** wording), and `flow/Pipeline` is the one parser that turns it
into GREEN / RED / RUNNING / NONE — every host words it differently, and two surfaces matching on words would
agree by luck.

The board shows one dot in the card's meta row and the console prefixes the request line (`CHECKS RED · …`),
because a red run while the task still reads CI_POLLING is exactly what a status word cannot show.

The human is tapped **once** per run, on the transition **into** red: an unattended poll that notified every
time would be a loop, and a red run that is already known is not news.

### The reply file is a review artifact, so its shape is prescribed in one place

The round brief (`ReviewSweepService.brief`), which is relayed **every** round and therefore reaches sessions
whose worktree was briefed before the wording changed: one block per comment (thread, the quoted line,
`FIXED | NO CHANGE | QUESTION`, and the reply), with necessary-and-sufficient as the test on every line.

The human reads the whole file in one pass to approve a round, so a per-comment essay is work handed to them,
not thoroughness. The sub-agent brief keeps the **style** and points at the shape rather than restating it.

### Drafted replies are a file, not state

`TaskViews` stats `review_replies.md` in the worktree and puts a boolean on the projection — presence, not a
count, since a number is the host's claim about the round and not one a file read can make. Both surfaces
announce it, because a human who does not know the convention ships a round and posts replies they never read.

**The announcement is also where it is read** (the owner's rule, 2026-08-21): `replies [task]` is a report
(`command/ReviewRepliesReport`) that puts every comment, its verdict and the reply that will be posted for it on
the screen the human already has. The console line names the verb; on the board the drafted-replies line **is**
the button that opens it. Approving a round by opening an editor in a worktree is a step nobody takes, which is
how a round gets shipped unread.

Two things it deliberately does: it reads the **file** rather than the card's badge (that one is announced only
where it is actionable, so a status that moved on would otherwise read as "nothing drafted"), and it prints
what does **not** fit the prescribed shape verbatim instead of dropping it — the file is agent-written, and a
parser that hid what it did not recognise would hide exactly the round that went wrong.

`GET /api/commands/{id}?about=<task>` is how a report narrows to one task: the same command the console types,
so no second endpoint.

**Presence is not enough: drafts belong to the round they were written in** (the owner's complaint, 2026-08-21
— a leftover file kept a task reading "action required" for a day). `ReviewDrafts.pending` is the one answer:
the file is there **and** newer than `mrCreatedAt`, because the ship that opened the round now open is the ship
that posted it.

**Who posts them decides that** (`CodeReviewConfig.shipPostsEveryDraft`): with `postReviewReplies=false`, or a
`reviewReplyAuthors` filter under which the agent posts some replies and deliberately keeps the rest, nothing
is ever spent — the answers are the human's to send, so the announcement stands until **they** end it.
`replies` still prints a spent file, since it is the only record of what was answered, but says it was already
sent rather than promising a ship will send it.

**jagt does not delete the file**, and that was decided against with a mechanism already written (2026-08-21).
A round stamp says a ship happened, never that the replies went out: posting is relayed to the agent and
deliberately off the critical path, so a dead session leaves "posted, not cleaned up" and "never posted" as the
same bytes on disk. Every deleting version also had to run *before* the ship it belongs to — the drafts of the
round being shipped are what that ship posts — which put it outside `ShipService`'s in-flight guard and in
front of every refusal, so a second click or a ship that then refused took the answers with it.

Not announcing a file is recoverable; unlinking it is not. The agent is still asked to delete what it posted
(the ship brief), and that stays a courtesy rather than the mechanism.

### One review sweep per task at a time

Whatever triggered it. The guard lives in `ReviewSweepService`, because the manual `sweep`, the auto-poll and
any future UI button all pass through it — two sweeps means the headless read paid twice and two briefs relayed
for one review round.

The other problem — ticks **queuing** behind a sweep that runs minutes — belongs to `Jobs`, which never runs a
job concurrently with itself, so `AutoReviewScheduler` keeps no guard of its own.

## Unattended work

### An open request is what the poller watches, never a status

The owner's rule, 2026-08-20. A reviewer writes on a request whatever the task is doing meanwhile, so
`AutoReviewCadence.polls` asks exactly two things: is there a request, and is the task still alive (DONE is the
one status that ends it — no worktree left to relay a round into).

Gating on CI_POLLING/REVIEWED meant a round the agent handed back stopped being read at all, and every comment
written after that — which is most of a bot's review — reached nobody until a human typed `sweep`.

The window is unchanged and stays the whole bound on polling: per round from `mrCreatedAt`, falling back to
`requestOpenedAt` so a request adopted by `resume` is polled instead of reading as untimeable.

Two consequences that are not optional: the relay is guarded (`relayIfChanged` — the same round read twice must
not interrupt the agent twice), and the poll may now find a task mid-work, which is fine because a sweep only
reads and drafts.

### Work that runs unattended must be visible while it waits

Not only after it acts. `AutoReviewCadence` is the **whole** auto-review policy — enabled, the interval ramp,
and `watch(task, now)` answering what a human is owed about one task (`task/AutoReviewWatch`: watching plus the
absolute next-poll stamp, window elapsed, off for this task, or nothing). `AutoReviewScheduler.decide` is a
translation of that same watch, so a card cannot promise a poll the scheduler will not make.

Both surfaces show it: the console's dashboard header carries `cadence.summary()` and each task a
`└ auto-review:` line; the board has the chip (`Board.autoReview`) and, per card, a `↻ <countdown>` **pulse** in
the meta row rather than a line of prose.

Whether polling runs at all is a property of the **install**, so it is stated once per surface and never
repeated per card — which is exactly why the words are the tooltip and only the countdown is on the card. A
watch that has **stopped** — window elapsed, or a task whose own `autoReview` is false while the install polls
— is that same slot wearing the **state** instead of a countdown, never a paragraph.

The countdown is an **absolute stamp** on the wire and formatted per surface (`DurationFormat.countdown` / the
page's own mirror), exactly as the two clocks on a card already are — a remaining-duration would be stale the
moment it was fetched. It is a **floor**, not a promise: the scan runs every 60s, so a poll shown as due
happens within the next tick.

### Unattended work is a declared kind, never a schedule a class keeps to itself

`job/Job` (id, one line of what the human gets, an interval or `null` for once at startup, `run()`) and
`job/Jobs`, the **one** ticker: each run on its own thread, never overlapping itself, a run that throws booked
against that job and nothing else — so no job needs a guard or a catch-all of its own.

A hidden `@Scheduled` cannot be listed, reported on or validated, which is the point. The `jobs` report (a
`GlobalCommand`, so both surfaces show it) names each job, its cadence, when it last ran and when it runs next:
**work nobody watches is visible before it acts, not only after.**

A report only answers whoever **opens** it, so `Jobs.summary` puts the two facts a human is owed unasked into
both headers: the soonest run of anything, and — outranking it, since the next run is not news while the last
one is broken — that a run threw. It is derived from the statuses already kept, never a second count.

An adapter's own workaround is a job **that adapter** contributes (the IDEA recent-projects cleanup), never a
permanent timer for everybody.

### What jagt did unattended is read back from its own log

`command/ActivityReport` tails `logging.file.name` (structured ECS JSON), keeps the entries that carry a `task`
key-value and renders them newest first for the `activity` verb and the board's Activity dialog.

The convention it depends on is the one already in force — **INFO for work nobody watched, nothing for a button
a human pressed** — so an in-memory ring buffer or a jagt-owned log file would be a second answer to "what
happened" **and** would not survive the restart after which a human looks.

It deliberately shows only work that named a task: `state.json` history already carries the status transitions
with who asked for them.

**One run, one log:** `surface/ui/SessionLog` empties the file and deletes the archives beside it before the
appender opens it, so the report is this session's work and nothing older — the owner's call (2026-08-18), and
the reason nothing gzipped is read back.

The file stays structured on **every** surface. `ConsoleLogging` used to try blanking
`logging.structured.format.file` for the console UIs, which would leave `activity` nothing to parse, and that
dead override is gone.

## Terminals, sessions and processes

### Master shell = full-screen TUI (Lanterna), one integrated screen

`MasterShell` runs a Lanterna `Screen`: command-output log on top, the dashboard table beneath it, the `jagt>`
input line pinned to the bottom row — all in one back-buffer, redrawn from scratch every frame (`render()`),
refreshed every `dashboard.refreshSeconds`. Resize is handled by `doResizeIfNecessary()` plus the full redraw.

**Do not reintroduce a JLine `Status` / scroll-region pinned bar, or any absolute-bottom cursor anchoring.**
That could not survive a terminal resize (DECSTBM resets on resize → an orphaned ghost dashboard and the prompt
flying to row 1), which cost many sessions.

`dashboard.reservedRows` caps the dashboard height so at least that many rows stay for output and input
(overflow → a "… +N" line).

**Terminal layout is testable, never "fix it blind"**: `orchestrator-backend/scripts/dashboard-layout-smoke.sh`
drives the jar in tmux and asserts the invariants (one dashboard header, input pinned to the bottom row,
dashboard above it) across startup, resize both ways, and task-count changes. **Run it after any change to
`MasterShell` rendering** — and pass `--orchestrator.ui=tui`, since the board is the default.

`tui-push-repaint-smoke.sh` is its sibling for the event-driven repaint: refresh 60s plus a status pushed
through `POST /mcp`, so only the listener can explain the redraw. Writing `state.json` directly fires **no**
listener — a test that mutates the file is testing the timer.

No-TTY (e.g. `gradlew bootRun`) falls back to a plain inline line-REPL.

### A detached launch gets its own session, never an ignored signal

`ProcessBuilder.start()` does not leave jagt's process group, and the terminal delivers Ctrl-C to the whole
**group**, so stopping the backend used to SIGINT the IDE jagt had started — one IntelliJ process hosts **every**
project window (measured 2026-08-18: same pgid, child dead on SIGINT).

`ProcessRunner.detachedFrom` therefore runs the command under `setsid`, or under
`perl -MPOSIX -e 'POSIX::setsid(); exec @ARGV'` where there is no `setsid` binary (macOS ships none).

The first attempt was `sh -c "trap '' INT QUIT HUP; exec …"` and it is the **wrong** fix — do not go back to
it: an ignored disposition is inherited by every descendant, so the IDE's own Stop button, Ctrl-C in its
embedded terminal and `kill -QUIT` thread dumps all stopped working for everything it spawned.

Both wrappers `exec`, so the returned `Process` is still the app and `destroy()` reaches it. Agents were never
at risk (the tmux server is already its own session) and kitty daemonizes itself with `--detach`; what **was**
at risk is everything started through `runDetached` — the editor and ttyd.

A wrapper that always starts also means a missing binary is no longer an `IOException`, so `runDetached`
**fails** the launch when the wrapper exits non-zero at once — without that, no ttyd installed reads as "no web
terminal configured".

### No GUI or keystroke automation, ever

System Events keystrokes race with the human typing: they land in whatever is focused.

Agent terminals are windows in a session host (`port/SessionHost`, tmux today); visibility comes from one Warp
window opened via `open warp://launch/jagt-agents` (a launch config generated into
`~/.warp/launch_configurations/`) whenever `tmux list-clients` shows nobody attached.

### kitty is one driver, not one per OS

`AbstractKittyTerminalDriver` holds everything — remote control, the per-session socket, tabs, reveal, close —
and each platform subclass supplies exactly two things: `bringToFront()` and `platformOptions()`.

macOS needs AppleScript to raise the app (Cocoa) and the Cyrillic `cmd+` keymap workaround. Linux needs
**neither** (the WM owns stacking, and kitty's own `ascii` shortcut fallback handles a non-Latin layout), so
`LinuxKittyTerminalDriver` overrides both with nothing and says why.

Selection is `orchestrator.platform` × `orchestrator.terminal` via `@ConditionalOnExpression`, and
`LinuxProfileContextTest` boots the linux profile so a condition typo fails in CI, not on someone's desktop.

`KittyTerminalDriver` drives kitty via its remote-control CLI (`kitty @ --to unix:<per-session socket>`): one
dedicated instance (`--single-instance --instance-group --listen-on -o allow_remote_control=yes`), tabs titled
and closable (unlike Warp). It runs **over tmux** (the tab execs `tmux attach`), so agents persist.
`closeViewerWindow` kills the instance by its socket path — macOS keeps the app alive after windows close, and
`close-os-window` / `--match all` are **not** kitty commands. Tab decoration comes from tmux `set-titles` → the
active window name (taskId).

### tmux

- **One task = one tmux window.** `openTaskWindow` kills same-named windows before spawning.
- Agent liveness in a window is detected via child processes of `#{pane_pid}` — `pane_current_command` always
  reports the shell (no job control in `sh -c` compound commands).
- After the agent exits, its window shows the tail for 15s and closes itself. **Never leave an interactive
  shell in an agent window** — it lingers forever and reads as a hung process.

### Warp

Closing the Warp window only **detaches** the viewer — agents keep running (a tmux feature, by design). Killing
is explicit: `done` / `remove` / `close_task_tab`.

Facts verified empirically plus a docs sweep (2026-07) — do not re-litigate:

- The URI scheme is the **entire** programmatic surface: no CLI, no IPC, no AppleScript dictionary, no MCP for
  the UI.
- Viewer tabs are opened via Tab Configs — TOML generated into `~/.warp/tab_configs/<session>.toml`
  (`[[panes]]` needs a mandatory `id`), opened with `warp://tab_config/<name>` (active window;
  `?new_window=true` for a fresh one). The tab runs `tmux attach` itself, no shell hooks.
- `new_tab` inherits the active tab's group; tab **groups** have zero API.
- Tabs are **not** closable programmatically (absent from the AX tree, no URI, Warp keeps them after process
  death). Whole windows **are** closable via addressed AXPress.

Hence viewMode `shared` is the default; `tab-per-task` leaves dead tabs for the human to close.

### MCP permission gating

Claude Code's auto-mode classifier silently blocks tool calls unless pre-approved.

The Master needs no permissions at all (it is Java; the committed root `.claude/settings.json` exists for a dev
Claude session working **on** jagt, which does call the jagt MCP).

Every sub-agent worktree (generated `.claude/settings.local.json`) needs `enableAllProjectMcpServers: true` plus
`permissions.allow: ["mcp__jagt-orchestrator", "Bash(git:*)"]` — the MCP tools **and** the agent's own git
(commit/push its task branch on `ship`), which nobody in the tmux window is watching to approve. Miss the MCP
entry → `ship`/`feedback` stall on an invisible prompt; miss the git entry → the agent freezes on `git commit`
or `git push`.

Safety on shared branches is **not** this allow-list — it is the detached upstream
(`GitService.detachUpstream`) plus prompt rules. The worktree is the agent's sandbox.

Regenerated only by `initialize_task`, so an **existing** worktree keeps its old file: patch it in place or
re-create the task to pick up a changed allow-list.

### Agent resource hygiene

Each sub-agent is a Claude Code session in a worktree, so each spawns its **own** language server (jdtls
~1–2 GB per Java worktree). They cannot be shared — worktrees have different uncommitted code, and LSP is
per-root.

Agents **keep** their LSP (code intelligence is worth the RAM), so jagt instead **reaps** each worktree's
language server on `done` / `remove_task` (`reapWorktreeProcesses`: `lsof` for processes whose cwd is the
worktree, `kill -9`) — an orphaned or hung jdtls survives the agent's exit otherwise.

`orchestrator.agent-disabled-plugins` writes `enabledPlugins: {"<name>": false}` into the worktree settings —
default **empty** (opt-in for RAM-constrained setups; disabling an absent plugin is a no-op).

## Pluggable by design

**A firm architectural invariant. Do not erode it.**

jagt targets Linux and macOS with swappable terminals, notifiers, editors and AI-agent runtimes (Claude Code /
Codex / Qwen / … — any MCP-capable CLI). Everything OS- or agent-specific lives behind a **strategy
interface**, selected by config, so adding a new one is "implement the interface + register a config value" —
**never** a hardcoded `if claude` / `if macos` sprinkled through the flow.

The agent-agnostic task flow (create worktree → provision → launch → talk over MCP) must stay free of any
single agent's assumptions.

### The six seams

| seam | selected by | today |
|------|-------------|-------|
| `UserNotifier` | `orchestrator.platform` | macos (default), linux |
| `TerminalDriver` | `orchestrator.terminal` | kitty (default), warp |
| `EditorDriver` | `orchestrator.editor-command` | any CLI launcher |
| `AgentRuntime` | `orchestrator.agent` | claude (default), codex |
| `CodeHost` | `orchestrator.code-host.type` | none (default), gitlab, github |
| `Tracker` | `orchestrator.tracker.type` | none (default), jira |

Ports live in `…port`, implementations in `…adapter`.

`JsonHttp` (`…adapter.http`) is the transport both reads go over, and it is **not** a seventh seam: it exists
so a host or a tracker is testable without a socket (every implementation's test drives a fake of it), and it
carries only the verbs a create-or-update needs.

### `AgentRuntime`

The pluggable AI-agent CLI. `launchCommand` **and** worktree provisioning (`provisionWorktree`, a template in
`AbstractAgentRuntime` plus one per-agent hook) both live here.

`mcp_client.js` is a **standard, agent-agnostic** MCP stdio↔HTTP proxy — keep it that way — and is linked by
the template. Only the config that declares it differs per agent (Claude: `.mcp.json` +
`.claude/settings.local.json`; Codex: `.jagt/codex/config.toml` with `CODEX_HOME` pointed at it — **not** at the
worktree's own `.codex/`, which is where a repository ships the project config layer Codex reads, and jagt
overwriting a tracked file is a change `ship` commits).

**Nothing outside the runtime may name an agent's files** — `WorktreeSetup` only calls `provisionWorktree`, and
`AgentSessions` only `displayName`.

### Which name holds the briefing is the runtime's to answer

The shared system-knowledge file is `AGENTS.md` (`AgentRuntime.SYSTEM_KNOWLEDGE_FILE`). Claude reads
`CLAUDE.md`, so its runtime symlinks `CLAUDE.md` → `AGENTS.md` — one file, never two copies to drift.

`AgentRuntime.systemKnowledgeFile` is asked **before** provisioning; afterwards jagt's own links are
indistinguishable from a checkout. A regular file already on one of those names came out of the checkout, so it
is the **project's**, and taking it costs the agent the instructions the repository ships **and** makes the next
`ship` commit the loss (jagt tracks `CLAUDE.md`; so does one configured project).

Claude's answer is then `CLAUDE.local.md` — loaded exactly the same (verified 2026-08-18), and the one name a
repository does not version. **Every other runtime refuses**, because an agent started without the safety rules
that file carries is worse than a task that would not start.

The bootstrap prompt therefore names **no** file: which one holds the briefing varies, and a prompt that says
`AGENTS.md` is wrong exactly where the fallback applies.

### `CodeHost`

Reads of a review request (the round a sweep decides on, and the branches a `resume` adopts, so neither costs a
model call) plus **exactly one** write: `createOrUpdateMergeRequest`, opening the artifact a human then reviews.

Never a push, a merge, a comment or an approval — those belong to the human's gates or to the agent's own MCP.
**A `CodeHost` that merges is a bug.**

The write is idempotent per (source, target) and **never retitles an open request** (`ship` reruns every review
round, and the human may have edited the title). Its one caller is `ShipService`, and only when a host is
configured.

`ReviewReader` deliberately does **not** fall back to the paid headless read when a configured host fails: that
would spend money invisibly and hide the misconfiguration. A partial REST read must **fail whole** — "no
unresolved comments + green pipeline" advances a task.

**Which protocol a host speaks is its business, not the seam's.** GitHub's read is one GraphQL query because
thread resolution exists nowhere in its REST API, and a round that cannot tell resolved from open relays every
comment it ever saw, forever.

Two GitHub facts a reader will not guess, and that make the difference between advising `deploy` and advising a
fix:

- The substance of a review usually sits in the review **body** rather than in inline threads, so a round read
  from threads alone can miss the whole request — and a CHANGES_REQUESTED decision must never come back with an
  empty comment list.
- `reviewDecision` is only populated where the repository **requires** a review; on an unprotected repo it is
  null however many people clicked Approve, so the reviewers' own latest states are the fallback.

`base-url` is the **web** root (the prefix that decides which URLs the host may claim) and each host derives its
own API endpoints from it — github.com serves its API from another host entirely.

Two flags have no GitHub counterpart on purpose: squash and delete-branch-on-merge are **repository** settings
there, and a `CodeHost` configures no repository.

The relay **line** is shared (`adapter/codehost/RelayLine`), so an agent never has to learn a second format for
a round.

### `Tracker`

Reads the one ticket a launch needs (title, labels, project) so `do <ticket>` costs no model call either.

**Read-only in the strong sense**: a tracker that transitions, comments or assigns is a bug — an issue's state
is the human's to move.

`service/TicketReader` routes it exactly as `ReviewReader` routes a host, including the no-fallback rule: a
tracker that **claimed** the ref owns it, and paying a model to retry the same read spends money invisibly and
hides the misconfiguration.

The assistant keeps one thing no configured tracker can do: follow a URL into a tracker jagt was never pointed
at.

Jira is read over the `v2` API on purpose — Cloud and Data Center both serve it, and the three fields read here
are identical in v2 and v3.

### What a port is

A new agent = one `AgentRuntime` implementation. A Linux port = new `UserNotifier` / `TerminalDriver` /
`EditorDriver` implementations. **Nothing else should need to change.**

## Master assistant

A headless one-shot, and now the **fallback**, not the path: `do <ticket>` needs the ticket read before a
worktree or agent exists, and `service/TicketReader` takes a configured `Tracker` first, `ReviewReader` a
configured `CodeHost` first. With both wired, the only call left is the ⌘K palette, which is a model call by
design.

What the assistant keeps that no configured API has: it **follows a URL** into a tracker — or onto a code host
— jagt was never pointed at.

### How it runs

`HeadlessClaudeAssistant` (`MasterAssistant`) spawns a one-shot
`claude "<prompt>" -p --setting-sources user,project,local --json-schema '<schema>'` (stdin `/dev/null` via
`ProcessRunner`).

It hardcodes **no** MCP server or path: `--setting-sources` makes the child inherit the human's **own** MCP
(portable, OS-independent), and `--json-schema` forces deterministic JSON. It runs from `java.io.tmpdir` so only
user-level MCP loads (no jagt project MCP → fewer tokens).

Project is resolved by intersecting the ticket's labels with each project's `labels`
(`TaskLauncher.projectsMatching`); the title is cached for the commit. Any failure → empty → `do` falls back to
an explicit project.

Headless `-p` does **not** auto-load plugin MCP without `--setting-sources` (verified: default `-p` sees zero
Jira tools), and narrowing it to `project` is equally fatal — the call runs from the temp dir, where project
scope alone resolves to **zero** MCP servers (verified 2026-08-13). **Keep `user` in the list**; the ~7k tokens
it costs are what buys the tracker tools.

### Inheriting is also the cheaper shape

Which is the opposite of what it looks like. An install may **declare** the servers instead
(`assistant.mcp-config` → `--strict-mcp-config`, no credential in jagt because such a file carries `${ENV}`
placeholders), and that is a **determinism knob only** — measured 2026-08-18, $0.09 cold against $0.04, because
the inherited prefix rides the prompt cache the human's own sessions keep warm while a jagt-private one is cold
on almost every call.

It pins the **servers** and nothing else: settings are still loaded, or a declared file's `${ENV}` placeholders
and the model would stop resolving (verified). Declared servers lose their plugin scope in tool names, so an
`allowed-tools` written for the inherited spelling silently stops matching — jagt cannot detect that without
parsing the declaration, so it is documented, not guarded.

### Every assistant call is metered

It is the only place jagt spends model money. `--output-format json` wraps the schema-validated answer
(`structured_output`, or `result` as a string) together with `usage` and `total_cost_usd`.

`UsageTracker` books it to the task that triggered it (persisted in `state.json`, so it survives a restart) and
to the session (in memory). **A call is billed before its answer is judged** — an errored call was paid for too.

Surfaces: the `TOKENS` dashboard column, the `stats` command and `GET /stats`. Sub-agent spend is **not**
visible here (it lives in the agent's own session) — never present these numbers as a task's total cost.

Measured floor per call (2026-08): ~25k input tokens of CLI baseline context, ~$0.41 on the inherited default
model vs ~$0.06 on haiku — which is why `orchestrator.assistant.model` **ships as `haiku`** (blank it to
inherit the human's own model). **The lever is fewer calls** (deterministic REST reads), not shorter prompts.

## Conventions

### Comments

**Go through the `sob-ai:commenting` skill every time, before writing or editing any comment** — no
exceptions, including a "quick" one-liner. Its hard gate decides: the default is **no** comment, one
non-obvious WHY at most.

Deleted on sight: narration of what the code does, an argument that a change is correct (that belongs in the
review, not in the file), how the code got this way, and a fact whose source of truth is elsewhere.

jagt's own history is the warning: 2349 comment lines against 7027 code lines, a build file explaining how the
dashboard renders and what a merge conflict means, and two comments still naming libraries deleted months
before.

**One more rule for this repo: a file may only speak its own layer.** The build file knows about the build; a
seam interface states its contract and never one implementation's mechanism.

### Every text jagt writes is read by an engineer in a hurry

Shortest form that still answers, lowest cognitive load, no story. One fact per line; a decision is the
decision plus at most one clause of why, not the road to it.

This binds command sentences, docs, prompts and commit messages alike. TODO.md was 670 lines of prose for 40
decisions before it was emptied (2026-08-18), and the owner's complaint was that nobody can read it.

**A decided decision is not a TODO.** It lives in the code, with the rule in this file and the road to it in git
history. TODO.md holds only what is still open, and holding nothing is its normal state. If an entry needs three
paragraphs, the code needs the explanation, not the file.

### The same standard is demanded of the agents

It is one section of `sub-agent-context.md` ("How you write") rather than a clause repeated per artifact: a
status line, a commit message, the review request's title and description, a reply and a code comment are the
same reader in the same hurry.

What sends the agent to the machine's **own** skill is one rule rather than a clause in that section, because
the answer is the same for code, for tests and for a review round as it is for prose: a skill outranks the
brief wherever the machine has one, house style belongs to whoever's machine it is, and jagt can ship none of
it.

Two limits: the brief is written by `initialize_task` alone, so a worktree that already exists keeps the
wording it was created with (recreate the task, or patch its file); and the relay `ship` names the description
rule itself, since that is the one artifact jagt does not write when a host is configured.

### Where a decision is written down

`USE-CASES.md` is the one-line answer per **situation** ("the request does not target the base branch → …").
When a case turns out to be non-obvious — or a session re-derives one that was already decided — append a row
there instead of only fixing the code.

**This file keeps the rules; `USE-CASES.md` keeps the answers.**

### Never use real project identifiers

Anywhere in this repo: code, tests, comments, docs, examples, fixtures. No real ticket keys or numbers, project
names, abbreviations, or issue titles from any actual project.

Always invent obviously fictional placeholders (`ABC-42`, "Widget layout is off"). The existing tests use
`ABC-N` ids — follow that.

### English only, everywhere

UI strings, placeholders, example phrases, comments, docs, test fixtures. The NL palette **accepts** any
language; jagt never **writes** one but English.

The single exception is functional, not textual: `KittyTerminalDriver`'s ЙЦУКЕН keymap (`map=cmd+м …`), where
the Cyrillic symbols **are** the key events.

### Markdown

Aim for ~120-character lines, hard max 150. Do not force awkward wrapping.

### A form field explains itself with a placeholder

Not with a paragraph parked next to its button. The `*-state` spans are progress and verdict slots (`reading
the ticket…`, `no task "x"`) and start **empty** — static prose there vanishes on the first submit (the
`finally` clears it) and never comes back, which reads as a bug.

### Prompt structure

Per Anthropic prompt-engineering guidance, and it applies to every prompt jagt **writes**: the sub-agent
context, the ship and review briefs, the headless assistant prompts.

- Wrap concerns in named XML sections (`<role>`, `<rules>`, `<output_format>`, `<examples>`).
- Forbid preamble explicitly.
- Damp deliberation with "respond directly", never "do not think" — that leaks `<thinking>` tags.
- **Never ask a CLI system prompt for JSON by wording alone** (cost without guarantee). The one place jagt takes
  JSON from a model is the headless assistant, where `--json-schema` actually constrains decoding. Otherwise
  JSON is only for persisted state (`state.json`).

## Testing etiquette

### Leave no trace

Smoke tests pass `--orchestrator.open-warp-window=false` (otherwise every run opens a Warp window that stays
behind), use a throwaway tmux session plus `ORCHESTRATOR_ROOT`, and kill the session and remove worktrees and
branches afterwards.

### No absolute macOS paths in defaults

An external binary is configured by **bare name** and resolved by `adapter/Executables`: PATH, then the known
install directories (Homebrew included, because a GUI-launched process has neither prefix on PATH), then the
per-user script directories, then **inside application bundles**.

`tmux-command` used to default to `/opt/homebrew/bin/tmux`, which made every task on Linux fail at "Failed to
start command". The agent CLI is deliberately **not** resolved: it runs inside the agent's tmux window under the
human's own PATH, and the string is what they read on screen.

`editor-command` / `editor-diff-command` are **lists**, so only the launcher is resolved and the arguments stay
the human's. A launcher nowhere to be found fails with the config **key** to set, not with the binary they never
chose.

**The bundle step is what makes the rule applicable to a desktop app at all**: an IDE's launcher lives in
`/Applications/<App>.app/Contents/MacOS/<name>` and lands in no bin directory, so defaulting `editor-command` to
`idea` without it broke `ide` on the owner's machine within the hour. **Do not weaken the resolver.**

### The suites

| suite | command | needs |
|-------|---------|-------|
| unit | `./gradlew test` | nothing — the fast hermetic gate |
| e2e matrix | `./gradlew e2eTest` | git + tmux (source set `src/e2e/java`, **not** in `test`/`check`) |
| board | `./gradlew boardTest` | Playwright's own Chromium (source set `src/boardTest/java`, not in `check`) |
| Linux drivers | `./gradlew linuxDriverTest` | Linux + binaries + a display (source set `src/linuxTest/java`, gated on `JAGT_IN_CONTAINER`) |
| console layout | `scripts/dashboard-layout-smoke.sh` | tmux + a built jar |
| console repaint | `scripts/tui-push-repaint-smoke.sh` | tmux + a built jar |

**Every fixed bug gets a regression unit test** (`sob-ai:unit-testing` rules), verified RED by actually
reverting the fix and running the test.

### The unit suite runs concurrently

JUnit parallel, methods **and** classes, which the self-contained style already allowed: no `@BeforeAll`, no
mutable statics, every file under a `@TempDir`.

A new test must keep that, and anything competing for a **machine-wide** resource declares it — the two that
pick a loopback port carry `@ResourceLock("loopback-ports")` + `@Execution(SAME_THREAD)`, because a port freed
to be probed is a port another thread can take first.

Only this suite: `e2eTest` shares branches and tmux sessions between rows, and `boardTest` seeds one
application's state.

### The board is tested in a browser

`boardTest` boots the app on a random port and drives the real page in Playwright's own headless Chromium. The
page's logic — the grid's order, the filter, which buttons a card offers, what a click POSTs, the SSE repaint,
the ⌘K palette's client-side verdict — runs nowhere else and was hand-checked until 2026-08-17.

Three write paths are `@MockitoBean`s because a real one would act on the developer's machine:
`CommandService`, `TaskLauncher`, `NaturalLanguageDispatch`.

The browser is Playwright's, never the machine's, so a Mac and a runner drive the same build; its shared
libraries are in `scripts/linux-test-deps.sh` — the **one** list, not a second one.

**Run it after any change to `static/`**, and assert through the **server** (seed `StateService`, stub a
command), never by evaluating JS in the page.

### The e2e matrix

`e2eTest` runs the flow once per `TaskFlowCase` with `orchestrator.agent=stub` (`StubAgentRuntime` — the one
non-deterministic participant replaced; every GUI driver is a Mockito double) and asserts an exact end state.

Two rules it lives by: widening coverage is adding a **row** to `TaskFlowCase.matrix()`, and a combination that
is **not** covered is named there with the reason — a silent gap reads as coverage.

Cleanup kills tmux sessions **by prefix**, because `tab-per-task` creates `<session>-<taskId>` ones the
configured name alone would leave behind.

It also asserts the **sentence** a flow returns, and `./gradlew test` cannot see it: reword a message and CI is
the first thing that notices, so run `e2eTest` before pushing one.

> [!WARNING]
> Row 1 leaves the branch behind when it fails, so rows 2–4 then fail with "branch already exists". Fix the
> **first** row and re-run before reading the rest as four bugs.

Two matrices, on purpose:

- `TaskFlowCase` × `TaskFlowMatrixTest` — CREATE → TEARDOWN across the viewer combinations.
- `ReviewRoundCase` × `ReviewAndDeployFlowTest` — everything between (ship, a round, deploy, revert, resume) on
  **one** combination, because a review round does not vary with how terminals are arranged. There the verbs go
  through the board's own HTTP endpoints and the agent reports over `POST /mcp` with its worktree header, so
  origins (`board` vs `mcp`) are asserted end to end and a surface cannot drift from the core. Its two doubles
  are `FakeCodeHost` and `MasterAssistant` — the second is a **guard** rather than a stub: nothing in these
  flows may reach a model any more, so a read that stopped routing through the host fails the run instead of
  paying for it.

### Linux is testable from a Mac

`scripts/linux-suite.sh` runs `test` + `e2eTest` + `linuxDriverTest` inside a container
(`docker/linux-suite.Dockerfile`).

`linuxDriverTest` is the only place the Linux drivers meet real binaries: the notifier's message is asserted off
the session bus via `dbus-monitor`, kitty is driven under Xvfb.

Anything a container cannot host — IntelliJ, the AppleScript raise, the Warp URI scheme, the real `claude` —
stays **named as uncovered** rather than faked. Two Linux behaviours are on that list **permanently** (decided
2026-08-18, not a gap waiting to close): the viewer being raised above other applications, and closing the
viewer. Both need a window manager with a human in front of it, so the `@Disabled` test in
`LinuxKittyTerminalDriverLinuxTest` documents the lead and no pipeline pretends to cover them.

### One set of steps for every host

`.github/workflows/ci.yml` and `.gitlab-ci.yml` run the same suites by calling the **same scripts**
(`scripts/linux-test-deps.sh` = the package list, `scripts/with-linux-desktop.sh` = Xvfb + session bus +
notification daemon, then the smoke scripts).

**A step that exists in one pipeline only, or a CI-only code path, is a bug**: green in CI and green on a laptop
must mean the same thing. Neither pipeline needs Docker — the container image is for macOS developers and
installs from that same deps script. `linuxDriverTest` is gated on **capability** (Linux + the binaries + a
DISPLAY), never on "which harness am I in".

**The build cache is for the hermetic suite only.** What `e2eTest` / `boardTest` / `linuxDriverTest` prove is
the **machine**, and no machine state is in a cache key, so all three opt out (`cacheIf` / `upToDateWhen`
false) — a restored result comes back green with nothing having run, on a fresh worktree and in a pipeline that
caches `~/.gradle` alike.

### Every new install requirement

Must be documented in `docs/installation.md` — **never install things silently.**

## Code quality

**The test is the litmus of the production code.** If a test needs ~5+ objects set up, or its cognitive load is
high, the smell is in the **production code** (poor decomposition or isolation), not the test. Fix the code so
the test goes light (`sob-ai:unit-testing` §5). Never paper over it with fatter test setup or shared fixtures.

**A test that needs more than ~3 mocks is telling you the class under it does too much.** Fix the class, never
the fixture.

### No god objects

Three collaborators per class is the target, **five is the hard ceiling** — and that ceiling holds for a class
that only **delegates**, because a delegating aggregate is exactly how one grows.

Over it, **group** collaborators into a cohesive component (composition, never inheritance) and let callers
depend on the part they use.

The ceiling is not advisory: `MasterShell` sat at eight and its test built the whole screen to check a parse,
which is how a 31-mock test happens.

**No class is over the ceiling today** (checked 2026-08-14: 70 classes, none above five, 47 at three or fewer).
A new aggregate is how that regresses: when a class would need a sixth collaborator, the answer is a registry of
small units (see `surface/mcp/McpTools`, and `Move.actions()` for the per-task verbs), **never one more field.**

### Records and Lombok

No positional null-soup: config and value records get a builder or `defaults()` + `withX` withers — never a
10-arg record constructor with a row of `null`s.

Lombok carries the **mechanical** boilerplate and nothing else: `@RequiredArgsConstructor` for injected final
fields, `@Slf4j` for the logger, `@With` for a record's positional copy-withers (1.18.46 supports `@With` **and**
`@Builder` on records — verified under the Java 25 toolchain).

Written by hand where the code is not mechanical: a constructor that validates or derives
(`OrchestratorPaths`), a wither that does more than copy one component (`TaskState.withStatus` stamps history),
and `TaskState.builder(project, worktree, status)` — Lombok's generated `builder()` cannot demand those three.

Prefer composition over many injected dependencies. SOLID and clean-code defaults have been standard for 30
years: apply them, do not reinvent them.

### The self-control loop (mandatory, every code + test change)

Run the changed tests through the `sob-ai:unit-testing` skill. If it reports a test as compositionally heavy or
high cognitive load, that is a signal to **refactor the production code** until the test is light — then re-run.

Deliver only when tests are **both** light and green **and** reviewed.

### Code review is mandatory after every code change, before committing

**And it is scoped to what this session touched** — never to "the working diff" and never to the branch.
Several sessions work in this tree at once, so the tree and the index hold their changes too, and
`/code-review` with no target reviews the whole branch since it left the remote **plus** everything
uncommitted, whoever wrote it.

State the scope, and state a **level** every time (the last one typed is remembered and silently applied to the
next call that omits it):

| when | run |
|------|-----|
| before committing | `/code-review medium <the paths you changed>` — the same explicit paths you stage |
| after committing | `/code-review medium <sha>^..<sha>` — a ref range is the only scope another session cannot widen while the review runs |

Stay at `medium` unless the change is genuinely subtle: every level above it fans out eight to ten finder
subagents plus one verifier per candidate location, and each of them re-reads the changed files and the whole of
this file.

Fix every real finding (or explicitly note why it is a non-issue), then re-review if the fixes are non-trivial.
**No commit lands unreviewed** — this is a hard gate, not a suggestion. (A shell hook can only *remind*; it
cannot invoke a skill, so this is enforced here as a workflow rule, not in `settings.json`.)

### Commit every finished piece of work, in the same turn it went green

**Permission to commit is standing; permission to push is not.**

Stage the explicit paths you touched, **never `git add -A`** — other sessions are in this tree.

Where the review skill cannot run (a harness with no subagents), read the diff yourself, say so in one line, and
commit anyway: a change with no logic in it — a string, a doc row, the assertions that follow one — is a diff
read, not a fan-out.

**Work left sitting in the tree is not delivered.**

## Build & run

The default run is the **web board**:

```sh
cd orchestrator-backend
./gradlew build stageJar
java -jar build/libs/jagt-run.jar        # board on 8290; prints the URL
```

Add `--orchestrator.ui=tui` (or `=both`) for the console. `bootJar` has a fixed, version-independent archive
name, so the run command never changes across releases.

**Run the staged copy.** `bootJar` rewrites `jagt.jar` in place — see the gotcha below. `RunningJarWatch` exists
because that symptom cost two debugging sessions.

`./gradlew bootRun` works but Gradle captures stdout → no TTY (`System.console()` is null) → the TUI falls back
to a plain inline line-REPL. Run the jar directly for the full-screen TUI. Java 25, port 8290.

Verify: `curl -s localhost:8290/state`.

> [!IMPORTANT]
> **`NoClassDefFoundError` during a startup failure or on `exit` is not a code bug.** Do not "fix" it by
> preloading classes.
>
> The missing class **varies** (`ThrowableProxyUtil`, `STEUtil`, `SpringBootExceptionHandler`, any lazily-loaded
> class) precisely because the cause is not any one class: `./gradlew build` rewrites the fat jar **in place**
> (same inode — verified), so rebuilding while a JVM runs from that jar corrupts its class loading, and the
> first not-yet-loaded class fails — which then **masks** the real error (e.g. "Port 8290 already in use")
> behind a confusing logback/Spring trace.
>
> It is expected and harmless: the old instance dies, just restart from the freshly built jar.

The **same cause has a second face** that looks nothing like it: a jagt that keeps running while you rebuild
answers 500 on whatever it had not loaded yet (`/status`, `/stats` first, while the board still renders) —
diagnosed twice as an endpoint bug before the inode was checked.

Avoid both by running the staged copy: `./gradlew stageJar`, then `build/libs/jagt-run.jar` — a symlink to a
per-build `jagt-run-<stamp>.jar`, so re-staging while an instance runs cannot touch the inode it holds. (A fixed
staged name had the same bug and reproduced it once.)

A process started from `jagt.jar` **says so** at startup (`RunningJarWatch`), because the alternative is
learning it from a 500 or from a `NoClassDefFoundError` in the shutdown hook — the first exception-carrying log
line of the process's life is usually the one Ctrl-C produces, which is why the crash looks like a logback bug.
`service/RunningJarWatch` reports it when it happens anyway.

Past sessions burned hours chasing this as a logback/preload bug. It is not.
