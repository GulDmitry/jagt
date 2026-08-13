# jagt — TODO / future ideas

Backlog of ideas, not commitments. Newest thinking at the top of each section.

## Roadmap — decided order (review of 2026-08-12)

In dependency order; each step is detailed in its section below.

| # | step | why it earns its place | est. |
|---|------|------------------------|------|
| 1 | `Move`/`Phase`/`Owner`/`Action` instead of the next-move String | fundament for BOTH the UI and gate validation | 1 d |
| 2 | `CodeHost` REST — review sweep first, then MR create/update | kills the dominant token spend + the "is it approved?" judgement flake | 2-3 d |
| 3 | `ship` = backend commit + push | kills the permission-classifier stall class; `SHIPPING` stops hanging | 1-2 d |
| 4 | Local web UI (SSE + `/api` + static kanban) | mouse-driven, phase-legible, spend visible | 3-5 d |
| 5 | Status-transition history in `state.json` | "which steps happened, how long did review take" | 0.5 d |
| 6 | NL fallback as a command palette (tier 2 of two-tier dispatch) | flexibility, off the hot path | 1-2 d |

Steps 2 and 3 are what move the remaining mechanics out of the LLM; 1 is a prerequisite for 4.

Independent of that sequence — small, each fixes something that is wrong TODAY (details in the sections
below). None of them blocks another, so they fit between the big steps:

| step | what is broken now | est. |
|------|--------------------|------|
| One sweep per task, whoever triggered it | a manual `review` during an auto-poll runs a SECOND headless read of the same MR: double spend, two briefs relayed to the agent | 0.5 d |
| Watchdog covers the states an agent can actually die in | it only watches NEW/IN_PROGRESS, so an agent that dies while SHIPPING (the state the README's own troubleshooting entry is about) is never flagged | 0.5 d |
| A Spring context-load test | the repo has NO `@SpringBootTest`: adding a bean or a cycle breaks startup while all unit tests stay green — wiring is currently proven only by the layout smoke script running the jar | 0.5 d |
| `OrchestratorProperties.defaults()` + withers | a 12-field config record with no builder; every test writes 9 positional `null`s, which is exactly the null-soup CLAUDE.md bans (its sibling `ConfigFile` already has defaults + withers) | 0.5 d |
| `prune` for stale task branches | `done` keeps the branch BY DESIGN and no path removes a finished one, so branches pile up forever and a repeated ticket trips "branch already exists" (the `git branch -D` plumbing already exists) | 0.5 d |

Done: `assistant.model: haiku` is the shipped default (2026-08-13) — ~6x cheaper on every `do`/`resume`/poll
($0.064 vs $0.41 a call, both with the MCP the reads actually need).
The other half of that step, `--setting-sources project`, was DROPPED as a trap, see the cost entry below.

Token accounting is already in (`stats` + `/stats`, the `TOKENS` dashboard column, per-task totals in
state.json) — the numbers it measured are what re-ordered this table.

## Architecture

### Cost: what a headless assistant call actually costs — MEASURED, not guessed
Measured 2026-08-12 with an identical trivial prompt (`Return the greeting hi`) run from a temp dir, so the
numbers are the per-call FLOOR, before any real ticket/MR content:

| invocation | input (cache-create) | output | cost |
|---|---|---|---|
| default model (opus), `--setting-sources project` | 38 441 | 60 | **$0.41** |
| `--model haiku`, `--setting-sources project` | 24 869 | 178 | $0.051 |
| `--model haiku`, `--setting-sources user,project,local` (today's default) | 31 719 | 155 | $0.064 |

Conclusions, in order of leverage — note the first one contradicts the assumption this entry started from:
1. **The model dominates, not the MCP surface.** 6-8x between the inherited default (opus) and haiku on the
   same prompt — the ratio bundles the price with a smaller baseline context, the rows are not equal-sized.
   DONE 2026-08-13: `orchestrator.assistant.model: haiku` is the shipped default in
   `application.yml` (blank it to go back to your own default model); these are mechanical extraction
   tasks — read a field, return JSON under a schema.
2. **~25k tokens is the irreducible baseline** of any `claude -p` process (CLI system prompt + built-in
   tools). It cannot be optimized away — only AVOIDED, by not spawning a process at all. That is the real
   argument for the `CodeHost` REST sweep: 40 polls per MR × ~$0.40 ≈ **$16 per merge request** on opus, ≈$2
   on haiku, $0 over REST. Note each poll pays full cache-CREATION, not a cache read: the 10-60 min cadence
   is far outside the prompt-cache TTL, so there is no warm-cache discount to hope for.
3. **The MCP surface costs ~7k tokens (+27%), and it is NOT optional — do not narrow `--setting-sources`.**
   Modern Claude Code DEFERS MCP tool schemas (fetched on demand), so hundreds of installed tools do not
   land in context; the 7k is the price of having any at all. Verified 2026-08-13 by asking the CLI to name
   its own `mcp__*` tools from the temp dir: `--setting-sources project` answers `NONE` (the call runs from
   `java.io.tmpdir`, which has no project scope), `user,project,local` lists the full set. Narrowing it
   would save 7k tokens and break every read — the tracker/code-host tools live in USER scope. If the
   human's global CLAUDE.md / skills / output styles are ever worth dropping, that needs a flag that keeps
   `user` MCP, not this one.

If a minimal MCP config is ever wanted anyway (`--strict-mcp-config --mcp-config <file>`), the server names
CANNOT be guessed — jagt does not know whether the tracker is Jira or Linear, the host GitLab or GitHub, or
what the human named them. They would have to be CONFIG: an `assistant.mcpServers: ["…"]` key that jagt
copies out of the human's own MCP config at call time, empty = inherit everything. Given the measured 7k,
this is a determinism nicety (a configured contract instead of "whatever is installed this week"), NOT a
cost lever — do not spend the complexity on it before steps 1 and 2 of the roadmap above.

Verified while measuring: `--output-format json` composes with `--json-schema`, and `--strict-mcp-config`
works with no `--mcp-config` at all (= a no-MCP call, which is what the tier-2 text→command mapper wants).

Two known holes in the accounting that shipped with it:
- **A killed call is unmeasurable.** `ProcessRunner` destroys the process on timeout and throws, so there is
  no envelope and therefore no usage — the tokens it already burned are unknown, not zero. It is logged as
  UNMEASURED rather than guessed. The only way to actually capture it is `--output-format stream-json`,
  accumulating usage from the message stream as it arrives; worth doing only if timeouts turn out to be
  common (the 6-minute review sweep is the candidate).
- **No breakdown by call type.** `stats` totals everything, so "polls vs ticket reads" — the one split that
  says where to optimise — has to be inferred from the call count. A `kind` on the recorded call
  (TICKET_READ / MR_READ / REVIEW_SWEEP) would make the `CodeHost` payoff measurable per category instead of
  in aggregate.

### Cut cost + raise determinism: mechanical host/tracker ops as backend code, not an LLM tool-loop
The master side is already LLM-free for routing (see the DONE entry below) — what remains in a model is the
mechanical outside work: the auto-review poll (`AutoReviewScheduler` → headless `claude -p` → code-host MCP)
and, on the agent side, the whole ship sequence (commit with an exact title, push, create the MR, post
replies, report back the URL) that jagt already fully specifies in prose.

Lever: pull the DETERMINISTIC, mechanical outside ops into the backend behind a `CodeHost` strategy
(GitLab / GitHub / … — sibling to the existing seams `AgentRuntime`/`TerminalDriver`/`UserNotifier`) and a
`Tracker` strategy (Jira / Linear / …). Highest value first:
- **Review sweep → code.** `MasterAssistant.readReview` → `ReviewFacts` is already a narrow, unit-tested
  seam and `ReviewSweepService` needs NO change: implement `CodeHost.readReview(mrUrl)` as a REST call and
  inject it in place of the headless read. 0 tokens, 0 model latency, no drift — and it removes the
  judgement call jagt currently delegates to a model ("approved=true only if actually approved by a human,
  not merely mergeable"), which is a field in the API, not an opinion.
- **MR create/update → code.** jagt already owns the title pattern + merge-request defaults in config;
  creating/updating the request is a REST call, not a judgement. Removes a class of flake (mis-formatted
  title, agent forgets to report the URL).
- **`ship` commit + push → code.** See the dedicated entry below.
- Leave the JUDGEMENT work in the agents: ticket distillation, the code itself, review replies. Those
  aren't mechanical.

This is opt-in and behind a strategy interface, so "pluggable by design" holds. It DOES lean on
tokens-in-backend (env vars) — already sanctioned in the two-tier-dispatch decision, and consistent with
the tracker/VCS-host-agnostic note (the strategy IS the abstraction that keeps `if gitlab` out).

embabel (investigated) is the WRONG tool for this and for orchestrating the CLI sessions: it's a framework
for building an agent that makes LLM calls in-process (Spring AI + GOAP planner over typed `@Action`/`@Goal`),
not for controlling external Claude Code processes. GOAP is overkill for jagt's ~11-state near-linear FSM
(already an explicit `TaskStatus`), and it drags in Spring AI + an LLM key + a Boot-4 compatibility question
into a backend that currently has ZERO AI deps. The only slot it could fill is an in-process LLM call
(`MasterAssistant` ticket→JSON), where bare Spring AI would already do — GOAP adds nothing. Revisit only if
jagt ever needs its OWN reasoning (LLM-judge review, summarization), and even then prefer plain Spring AI.

First experiment: `CodeHost.readReview(mrUrl)` REST impl wired into `ReviewSweepService` in place of the
headless read; measure the token drop with the `stats` counter — that validates the whole "thicker app →
thinner, cheaper sessions" hypothesis on one slice before committing to the full seam.

### `ship` should commit and push from the backend, not by instructing the agent
`OrchestratorTools.ship` writes the agent a five-step prose instruction: commit with EXACTLY this title,
push branch, create the MR, post drafted replies, report `CI_POLLING` with the URL. Every step of that is
deterministic and already belongs to the backend — `GitService` holds the per-repo lock, `mrTitlePattern`
lives in config, MR create is a REST call (previous entry). Leaving it to the agent buys three failure
modes: the permission classifier can silently stall `git commit`/`git push` in a window nobody is watching,
the title can come back reworded, and the status/URL report can simply not happen (hence the defensive
"CI_POLLING requires the MR link in the message" validation).

Target shape: `ship` = (1) `GitService.commitAll(worktree, title)`, (2) push the task branch,
(3) `CodeHost.createOrUpdateMergeRequest(...)`, (4) status → `CI_POLLING` with the URL, all in-process.
`SHIPPING` stops being a state you can hang in (it becomes momentary), the whole "ship again to recover a
dead agent mid-ship" recovery path and its `shipGate(SHIPPING, !agentLive)` special case disappear, and the
`update_agent_status` URL validation becomes unnecessary.
Safety is unaffected: pushing the task branch from the backend is what `deploy` already does, shared
branches stay untouched, the detached upstream still guards a bare `git push`, and `ship` remains the
human's explicit approval gate. The agent keeps exactly the work that needs judgement: writing the code and
drafting review replies (`postReviewReplies` / `reviewReplyAuthors` still route those).

### NL fallback — tier 2 of the two-tier dispatch
Tier 1 (grammar → direct `OrchestratorTools` call) is what `MasterShell` already does. Tier 2 makes free
text work without ever putting a model on the hot path:
1. Parse input as a grammar command → execute deterministically (today's behaviour, ~95% of interactions).
2. No grammar match / free text ("залей ту задачу с логином") → hand the raw text to a LEAN headless Claude
   that maps it to a grammar command → VALIDATE (`SAFE_ID`, project mapping, the same gates the command
   would hit) → execute. Tokens + latency only here, only when flexibility is actually used. The LLM never
   executes: it only PROPOSES, deterministic code executes after the gate (asymmetric-failure-cost rule).
   Use the same stripped invocation as the assistant fix above (`--strict-mcp-config` with an EMPTY server
   list — text→command mapping needs no MCP at all, plus `--append-system-prompt "<grammar + task list>"`).
   In the web UI this is the Cmd-K command palette; in the TUI it is just what happens on an unknown command.
Prefer headless haiku over a resident local model: a 3-7B local model adds 4-8 GB RAM (the machine already
swaps — see the jdtls incident); headless `-p` holds nothing resident and, with a stripped context, is
nearly as cheap. Revisit a local model only if API cost ever dominates.

### One sweep per task, wherever it was triggered from
The in-flight guard lives in the WRONG class: `AutoReviewScheduler.poll` holds `inFlight`, but the manual
`review` command goes `MasterShell` → `ReviewSweepService.sweep` directly, on the shell's worker thread. So a
human typing `review` while an auto-poll is already running spawns a SECOND headless read of the same merge
request. Two consequences, both now measurable with `stats`: the spend doubles for that round, and both
sweeps can call `writeTaskContext`, handing the agent two briefs for one review round (it may fix the same
comments twice, or interleave them).
Fix: move the guard down into `ReviewSweepService` (a per-task lock/`Set` there), so EVERY trigger — manual,
scheduled, or a future web-UI button — is serialised per task by construction. The scheduler's own set then
becomes redundant. A second `review` should say "a sweep is already running for ABC-1" rather than queue.

### Watchdog only watches two of the eleven statuses
`WatchdogService` flags a task only when it is `NEW` or `IN_PROGRESS` (plus a silent MCP + no tmux activity).
An agent that dies at any other point is invisible to it — most importantly `SHIPPING`, which is exactly the
failure the README's troubleshooting table documents ("Task stuck at SHIPPING, no MR appears"): recovery is
possible only because the human notices and types `ship` again. `REVIEW_PENDING` after a relayed review round
has the same hole (the agent is supposed to be working on the fixes).
Fix: watch every status where jagt EXPECTS the agent to be doing something — NEW, IN_PROGRESS, SHIPPING, and
REVIEW_PENDING while a relayed brief is unanswered — and keep ignoring the ones where idling is correct
(CI_POLLING, REVIEWED, APPROVED, DEPLOYED, DEPLOY_CONFLICT, DONE). The window-activity check already prevents
false positives for a busy-but-quiet agent, so widening the status set is cheap.

### Prove the pluggable seams with a second implementation each
"PLUGGABLE BY DESIGN" is an invariant with, today, exactly one implementation behind most of it:
`AgentRuntime` = claude only, `UserNotifier` = macos only, `EditorDriver` = one CLI driver. Only
`TerminalDriver` has two (kitty + warp), and it is the only one we know actually abstracts anything — the
other interfaces have never been forced to accommodate a second shape, which is where such abstractions
usually turn out to be leaky.
The cheapest proof, and the most useful one: a second `AgentRuntime` (Codex or Qwen — `launchCommand` +
worktree provisioning + that agent's own MCP config file, `config.toml` instead of `.mcp.json`). It also
unblocks the E2E matrix entry below, which assumes a STUB runtime exists for CI. Do it before the seam
accumulates more Claude-shaped assumptions, not after.

### `deploy` has no undo
`deploy` is the one outward write in the whole system (task branch → `deployBranch`, pushed), and there is no
way back through jagt: if the merge breaks the deploy branch, the human leaves the tool and fixes it in git
by hand — at the exact moment they are under pressure. A `revert <ticket>` that creates a REVERT commit for
that task's merge on `deployBranch` and pushes it (human-gated like everything else, never a force-push, and
refused when the branch has moved on in a way that makes the revert ambiguous) would close the loop that
`deploy` opens. It also needs a status: `DEPLOYED` → `REVERTED`, so the dashboard stops claiming the change
is live.

## Automation

### Make the auto-review poll free
Every auto-review tick spends a headless `claude -p` (the dominant cost measured above) on a mechanical
read. That is exactly what the `CodeHost` REST sweep removes; until it lands, `autoReview.enabled`
defaulting to `false` is the cost guard.

### Finished task branches accumulate forever — add `prune`
`done` deliberately keeps the task branch (the work must survive a cleanup), and no path ever removes a
FINISHED task's branch, so every ticket leaves one behind: the base repo slowly fills up, `git branch` becomes
unreadable, and re-running a ticket hits the "branch 'ABC-42' already exists" warning that `do … recreate|
resume` exists to work around.
The git plumbing is already there — `GitService` runs `git branch -D` for the `recreate` strategy and for the
throwaway `jagt-deploy-*` branch — so this is a SELECTION and CONFIRMATION problem, not new git work. Add a
`prune` command that LISTS the task branches whose review request is merged/closed (once `CodeHost` can answer
that; before then, branches with no unmerged commits vs `deployBranch`) and deletes only what the human
confirms. Never automatic, never on `done`: deleting work is exactly the class of action jagt keeps
human-gated. Same for any `jagt-deploy-*` worktree that survived a crash.

## UX

### The dashboard shows an enum, not a process — model the phase, then render it
This is the root of "ревью/шип/деплой непонятно". Eleven `TaskStatus` values, of which `REVIEW_PENDING` /
`CI_POLLING` / `REVIEWED` / `APPROVED` all read to a human as the single word "review". And the next-step
hint is PROSE: `NextMove.forStatus` returns a `String`, so it can be neither turned into a button nor
validated — `ship`'s real legality lives in `OrchestratorTools.shipGate`, and the dashboard advises
independently of it. Two sources of truth for "what can I do now".

Fundament (do this before any UI work): `NextMove.forStatus(status)` →
`Move(Phase phase, Owner owner, List<Action> actions, String hint)` where `Owner ∈ {AGENT, YOU, CI}` and
`Action = (id, label, primary)`, with legality computed by the SAME code as the command gates. Then:
- the TUI renders phase + owner + actions instead of a sentence;
- a UI can offer exactly the actions the server declared legal — an illegal move becomes unrepresentable;
- the eleven statuses collapse into five rail steps a human reads at a glance:

```
BUILD ──▶ CHECK ──▶ REVIEW ──▶ READY ──▶ DEPLOY ──▶ DONE
NEW          REVIEW_   SHIPPING    REVIEWED   DEPLOYED
IN_PROGRESS  PENDING   CI_POLLING  APPROVED   DEPLOY_CONFLICT
                       CI_FAILED
🤖 agent     👤 you     🤖/⚙️ CI     👤 you     👤 you
```

Keep `TaskStatus` as the persisted SSOT — `Phase` is a projection for humans, not a second state machine.

### Local web UI (mouse-driven), TUI stays as the fallback
The CLI dashboard is fine as a monitor and bad as a control surface: no clicking, no per-task actions, no
timeline, no cost. The backend is already Spring Boot Web on 8290 with `/state` + `/status`
(`McpController`), so a local UI is a small addition, not a new stack:
- `GET /api/tasks` — the dashboard projection plus `phase`/`owner`/`actions` from the entry above;
- `GET /api/events` — SSE. `StateService` already funnels every mutation through one lock, so a listener
  list fired from `putTask`/`updateTask`/`removeTask` is ~15 lines and gives push instead of polling
  (the same trigger the TUI could use to repaint on change rather than on a timer);
- `POST /api/tasks/{id}/actions/{action}` — executes ONLY what the server itself listed as legal;
- `src/main/resources/static/` — vanilla JS/CSS, no build step and no CDN (must work offline; a strict
  no-external-assets page also keeps the jar self-contained). Served by the same jar, opened at
  `localhost:8290`.

Card per task in a column per phase: alias + ticket + title, owner badge, time-in-current-state, the MR
link, the drafted-replies indicator, and buttons = the legal actions (`ide`, `ship`, `sweep`, `deploy`,
`done`, `focus`). Header: how many tasks are waiting on YOU, and today's master-side token cost (`stats`).

Phase 2, only if the basic UI proves itself: embed the agent terminal instead of switching windows —
`ttyd -W tmux attach -t jagt` in an iframe makes `focus` a click in the browser. That is a new install
requirement, so it goes into README's Prerequisites table (never install silently).

Do NOT fork the rendering logic: extract one `TaskView` projection consumed by the TUI, the `/status` text
and the JSON alike. And keep the TUI — it is covered by the layout smoke test and is the fallback when
there is no browser.
Rejected alternatives: an IntelliJ plugin (months of work, and it would bind the UI to one editor against
the pluggable-by-design invariant); Electron/native (same cost, more of it); Lanterna mouse support (it
does have `MouseAction`, but clicking inside an ASCII table treats the symptom, not the diagnosis above).

### Status-transition history in `state.json`
"Which steps has this task actually been through, and how long did it sit in review?" is unanswerable today
— `TaskState` keeps only the current status plus `lastActiveTimestamp`. Add an append-only
`history: [{status, at}]` (capped, e.g. last ~50 entries so the file stays small) written by the same
`withStatus` path that already stamps the timestamp. That single field powers the card timeline in the UI,
"time in current state" in both surfaces, and later any cycle-time statistics ("this ticket spent 6 h
waiting on me").

### The drafted review replies are invisible until you go looking for them
The human-in-the-loop contract says every auto-review round hands the human two artifacts: the local diff and
`review_replies.md`. The diff is one `ide` away — but nothing anywhere SIGNALS that drafted replies exist.
The file is written inside the worktree by the agent; `state.json` does not know about it, so neither the
dashboard, the `→` next-move line, nor the notification mentions it. A human who does not already know the
convention will `ship` a round and silently post (or not post) replies they never read.
Fix: have the backend check for `review_replies.md` in the worktree when it renders a task (it already reads
the worktree path) and surface "N drafted replies" as a dashboard detail line plus part of the REVIEW_PENDING
next-move. That single signal is also the web UI's drafted-replies indicator, and it makes the
`postReviewReplies` / `reviewReplyAuthors` config comprehensible — right now their effect is invisible.

### Repaint the TUI on state change, not only on the timer
The dashboard currently refreshes every `dashboard.refreshSeconds`. A `StateService` change listener would
repaint the moment an agent's MCP call lands (0 latency, no busy poll), keeping the slow tick only for the
relative "ACTIVE" clock. It is the SAME listener the web UI's SSE stream needs — build it once, use it twice.

- tmux status bar styled as clickable job "tabs" (alias + status, active highlighted) so `shared` viewMode
  reads like native tabs. Largely superseded by the web UI entry above; keep only if the TUI stays primary.

## Docs / clarity

- `review` is a confusing name: it does a full sweep of the review request (pipeline + comments) and relays
  a brief — it does not "review" anything itself, and now that `autoReview` polls automatically, a manual
  trigger is an escape hatch, not a workflow step. Rename to `sweep <ticket>` ("check it now") and keep
  `review` as a hidden alias for muscle memory.

## Product shape — bigger questions, unscheduled

### Nothing limits how many agents you start, and each one costs GBs
`do` spawns an agent per ticket with no admission control. Each is a full Claude Code session with its own
language server (~1-2 GB for a Java worktree, per CLAUDE.md's resource-hygiene note — the machine already
swapped once because of it), plus a worktree checkout on disk. Five tasks is a different machine than two,
and the human finds out by watching everything crawl.
Worth a `maxConcurrentTasks` (config, default something honest like 3): `do` beyond it either refuses with
"finish or `done` one first" or QUEUES the task as a new pre-NEW status that the scheduler starts when a slot
frees. Queueing is the nicer behaviour but adds a state; refusing is one `if` and already an improvement over
silently thrashing. Either way the dashboard should show the cap, because the limit only helps if it is
visible before it is hit.

### One task = one repository
A ticket that touches two repos (backend + frontend) has no representation: it becomes two unrelated tasks,
each with its own branch, MR, review cycle and alias, and nothing ties them together — not the dashboard, not
`ship`, not `deploy`. The human keeps the relationship in their head and has to remember to ship both.
This is a real shape question, not a small feature: does a task grow a LIST of (project, worktree, branch)
tuples, or does jagt gain a "change set" that groups tasks? The first breaks the "one worktree = one agent"
assumption that `X-Working-Directory` scoping rests on; the second keeps every current invariant and adds a
grouping layer on top, which is probably the answer. Do not start it before the phase/action model (roadmap
step 1) exists — a group's state is a function of its members' states, and that needs the projection first.

### Secrets are copied into every worktree and only the happy path cleans them up
`worktree.copyGlobs` deliberately copies gitignored local files — `.env`, `*.pem`, `*.p12`, keystores — into
each worktree so the app can actually run there. That is the right call and it is documented, but it means N
copies of production-ish credentials live in sibling directories of the repo, readable by every agent
process, and they are removed only when `done` succeeds in deleting the worktree. `removeWorktree` is
best-effort and logs-and-continues in places, and a crashed/abandoned run leaves the copies behind
indefinitely.
Worth: a startup sweep that reports (not silently deletes) `*-<projectKey>` sibling directories with no
matching entry in state.json, so orphaned worktrees — and the secrets in them — surface instead of rotting.
Also worth deciding explicitly whether the copied set should be narrower by default than "any `*.pem`".

### `state.json` has no history and no backup
Writes are atomic (temp + ATOMIC_MOVE), so a crash cannot truncate it — but a bad manual edit, a botched
migration, or a future serialization bug takes every task with it, and the file is the SSOT for what jagt is
even doing. One `.bak` kept from the previous successful write (or a small ring of them) is a few lines and
turns "all tasks lost" into "restore the file". Cheap insurance for the single point of failure in the design.

## Testing & portability

### Generic wording for the GitLab-leaning internal labels (low priority)
`mrUrl` / "MR" / `CI_POLLING` are GitLab-flavoured INTERNAL names — fine as-is, but user-facing text could
say "review request" / "pipeline or checks" generically. Not worth a churny rename until a non-GitLab host
is actually wired. (The invariant itself — never hardcode a tracker or code host — lives in CLAUDE.md.)

### No Spring context test — wiring is only proven by running the jar
There is not a single `@SpringBootTest` in the repo. Every unit test builds its collaborators by hand, so a
missing bean, an ambiguous injection point, a circular dependency or a broken `@ConfigurationProperties`
binding cannot fail the test suite — it fails at STARTUP, i.e. in front of the human. Today the only thing
standing between a wiring break and a broken release is `dashboard-layout-smoke.sh`, which happens to boot
the jar for a different reason.
Add one test that loads the context (with the scheduler disabled and the platform drivers stubbed/no-op, per
the leave-no-trace etiquette) and asserts it starts. It is a handful of lines and it covers every bean the
project will ever add — including the seams that must stay unambiguous, like "exactly one `MasterAssistant`
implementation, injected only by `MeteredAssistant`" (an off-the-books model call is otherwise a compiling,
wiring, silent mistake).

### Config records: `OrchestratorProperties` still has no `defaults()`
`ConfigService.ConfigFile` and its sections have `defaults()` + withers precisely so nobody writes positional
null-soup — but `OrchestratorProperties` is a 12-field record with neither, and it is the one tests must
construct. Every test that needs a `StateService` therefore writes nine consecutive `null`s, now copied into
four test classes; the argument list is unreadable and a reordering of the record's components would silently
change what those tests configure. Give it the same `defaults()` + `withX` treatment (or a builder) and the
duplication in the tests collapses to `OrchestratorProperties.defaults().withStateFile(path)`.
This is the CLAUDE.md "the test is the litmus of the production code" rule pointing at a specific class.

### Verify the build on Linux, then actually implement its drivers
Confirm `./gradlew build` and the runnable jar work on Linux (Java 25, Node, tmux, git present). The core
is OS-neutral; the only OS-specific code is behind the platform strategies (`UserNotifier`/`TerminalDriver`/
`EditorDriver`). First milestone: backend boots + the deterministic tests pass on a Linux runner, even
before Linux impls of the three drivers exist (they can no-op / fail-soft until then).
Second milestone, the one that makes Linux a supported target rather than a compiling one: a `libnotify`
(`notify-send`) `UserNotifier`, a Linux `TerminalDriver` (kitty already exists there — the existing impl may
port with only the socket path changing), and an `EditorDriver` for the `idea`/`code` CLIs. Nothing else
should need to change; if something does, that is the pluggable-by-design invariant leaking and worth fixing
rather than working around.

### Automated end-to-end test harness across all config combinations, with a deterministic oracle
Goal: one automated suite that exercises the WHOLE task flow (create worktree → provision → launch →
talk over MCP → ship/review/deploy/done) across the full matrix of swappable pieces and config flags, and
asserts a **deterministic expected result** for each combination — so any regression in any combo is caught
without hand-testing.

The matrix (Cartesian product of the strategy seams + config):
- `AgentRuntime` (`orchestrator.agent`: claude / codex / … — stub/fake runtime for CI),
- `TerminalDriver` (`orchestrator.terminal`: kitty / warp),
- `UserNotifier` (`orchestrator.platform`),
- `EditorDriver` (`orchestrator.editor-command`),
- config flags: `viewMode` (shared / tab-per-task), `postReviewReplies`, `reviewReplyAuthors`,
  branch strategies (fresh / resume), deploy on/off, plan mode, `autoReview` on/off, etc.

Design notes:
- Needs a **deterministic oracle**: fake/record-replay the external, non-deterministic pieces — a stub
  `AgentRuntime` that emits scripted MCP calls instead of a real LLM, a throwaway local Git origin, a
  throwaway tmux session, and headless terminal/editor/notifier drivers (no GUI). Then every combo has a
  fixed expected end state (state.json transitions, branches/worktrees created + cleaned, MR/CI mocked).
  A `CodeHost` seam makes this dramatically easier — a fake `CodeHost` replaces "mock an LLM reading a
  merge request", which is the least testable thing in the system today.
- Assert on OBSERVABLE state, not timing: final `TaskStatus`, git refs/worktrees present-or-gone, files
  written into the worktree, notifications emitted. Follows the existing smoke-test etiquette (throwaway
  tmux + `ORCHESTRATOR_ROOT` + `--orchestrator.open-warp-window=false`, leave no trace).
- Run the SAME matrix on macOS now and on Linux once its drivers exist — the harness is the portability
  gate: "does combination X behave identically on both OSes?" Think through how to keep the expected-result
  oracle OS-independent (the flow is OS-neutral; only the driver side effects differ).
