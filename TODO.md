# jagt — TODO / future ideas

Backlog of ideas, not commitments. Newest thinking at the top of each section.

## Roadmap — decided order (review of 2026-08-12)

In dependency order; each step is detailed in its section below.

| # | step | why it earns its place | est. |
|---|------|------------------------|------|

| 2 | ~~`CodeHost` REST — review sweep + MR create/update~~ DONE in the seam; the create call is UNWIRED (step 3 wires it) | kills the dominant token spend + the "is it approved?" judgement flake | done |


| 5 | ~~Status-transition history in `state.json`~~ DONE — `TaskState.history` + `statusSince()`, shown on both surfaces | "which steps happened, how long did review take" | done |
| 6 | NL fallback as a command palette (tier 2 of two-tier dispatch) | flexibility, off the hot path | 1-2 d |

Steps 2 and 3 are what move the remaining mechanics out of the LLM; 1 is a prerequisite for 4.

Done: `assistant.model: haiku` is the shipped default (2026-08-13) — 8x cheaper on the same call
($0.051 vs $0.41, both measured on `--setting-sources project`). The shipped invocation keeps the full
`user,project,local` MCP the reads actually need, so its real figure is $0.064 a call — ~6x under the opus
row, but that pair is NOT apples-to-apples: opus was never re-measured with the full MCP.
The other half of that step, `--setting-sources project`, was DROPPED as a trap, see the cost entry below.

Done 2026-08-13, the phase model and the web board (roadmap steps 1 and 4): `Move`/`Phase`/`Owner`/`TaskAction`
+ `TaskView` are the one projection every surface renders, `CommandService`/`TaskLauncher` the one way an action
runs or a task starts, and `OperatorUi` (`orchestrator.ui`) is the seam that makes the board the default and the
console a switch. The board is vanilla static assets + `/api/tasks`, `/api/tasks/{id}/actions/{action}` and an
SSE stream fed by `StateService.onChange`. Left for later: embedding the agent terminal (ttyd, phase 2) and the
drafted-replies indicator below, which now only needs a field on the projection.

Token accounting is already in (`stats` + `/stats`, the `TOKENS` dashboard column, per-task totals in
state.json) — the numbers it measured are what re-ordered this table.

Done 2026-08-13, the review-sweep half of step 2: `codehost/CodeHost` (+ `GitLabCodeHost` over v4 REST,
`JsonHttp` transport port) and `service/ReviewReader`, which routes a sweep to REST when a configured host
claims the URL and to the metered assistant otherwise. `ReviewSweepService` kept its guard and lost the
metering plumbing. Opt-in via `orchestrator.code-host.{type,base-url,token}`; unconfigured = the old paid
path. Two rules worth keeping: a REST failure never falls back to a paid read (invisible spend + a hidden
misconfiguration), and a PARTIAL read fails whole, because "no comments + green" advances the task.

Done 2026-08-13, the second `AgentRuntime` (see "Prove the pluggable seams"): `CodexAgentRuntime`, which
forced `provisionWorktree` into the seam — worktree provisioning left `OrchestratorTools`, the context file is
now `AGENTS.md` for everyone (Claude gets a `CLAUDE.md` symlink), and the flow no longer names an agent's
files or prints "Claude" (`displayName()`).

Done 2026-08-13, hygiene + metering: `state.json` keeps its previous version as `state.json.bak` and a read
that cannot parse the primary recovers from it (bad file set aside as `state.json.corrupt`); with no usable
backup it THROWS rather than starting empty over an existing file. `WorktreeOrphanScanner` reports leftover
worktrees and the secret copies in them (`GET /orphans`). `stats` splits the session spend by
`AssistantCallKind`, and `UsageTracker` derives the session total from that split so the two cannot drift.

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

One hole left in the accounting: **a killed call is unmeasurable.** `ProcessRunner` destroys the process on
timeout and throws, so there is no envelope and therefore no usage — the tokens it already burned are unknown,
not zero. It is logged as UNMEASURED rather than guessed. The only way to actually capture it is
`--output-format stream-json`, accumulating usage from the message stream as it arrives; worth doing only if
timeouts turn out to be common (the 6-minute review sweep is the candidate).
(The other hole is closed: `stats` now splits the session by `AssistantCallKind` — TICKET_READ / MR_READ /
REVIEW_SWEEP — biggest first, which is also how the REST payoff becomes visible per category.)

### Cut cost + raise determinism: mechanical host/tracker ops as backend code, not an LLM tool-loop
The master side is already LLM-free for routing (see the DONE entry below) — what remains in a model is the
mechanical outside work: the auto-review poll (`AutoReviewScheduler` → headless `claude -p` → code-host MCP)
and, on the agent side, the whole ship sequence (commit with an exact title, push, create the MR, post
replies, report back the URL) that jagt already fully specifies in prose.

Lever: pull the DETERMINISTIC, mechanical outside ops into the backend behind a `CodeHost` strategy
(GitLab / GitHub / … — sibling to the existing seams `AgentRuntime`/`TerminalDriver`/`UserNotifier`) and a
`Tracker` strategy (Jira / Linear / …). Highest value first:
- ~~**Review sweep → code.**~~ DONE (2026-08-13): `CodeHost.readReview(mrUrl)` + `GitLabCodeHost`, routed by
  `ReviewReader`; `ReviewFacts` moved to `model` since it is no longer an assistant-shaped thing. 0 tokens, 0
  model latency, and the "approved by a human, not merely mergeable" judgement is now the approvals endpoint.
  Still open on the read side: a second host (GitHub) — the seam has one implementation, same critique as
  below — and `Tracker` for the ticket read, which is still the only reason `do` spawns a model at all.
- ~~**MR create/update → code.**~~ DONE in the seam (2026-08-13): `CodeHost.createOrUpdateMergeRequest` +
  `hostsRepository` + `GitLabCodeHost`, idempotent per (source, target) and never retitling an open request.
  `codeReview.mergeRequestDefaults` stopped being fiction while I was there — README documented
  `removeSourceBranch`/`squash`, `CodeReviewConfig` had no such field, and the flags now feed the create call.
  NOT WIRED: `ship` still relays the MR step to the agent in prose. Wiring it is step 3's job, and it is what
  finally removes the "agent forgets to report the URL" flake — the capability alone removes nothing.
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

First experiment DONE (2026-08-13): `GitLabCodeHost.readReview` is wired in behind `ReviewReader`. What is
still MISSING is the measurement it was supposed to produce — point the config at a real host, run a task
through a review round, and compare `stats` against the pre-REST numbers above. Until somebody does that, the
token drop is arithmetic (a poll that spawns no process costs nothing), not evidence.

### DONE 2026-08-13 — `ship` commits and pushes from the backend
`ShipService` does it in-process when a `CodeHost` owns the repository (commit → push → create/update the
request → CI_POLLING with the link), and `OrchestratorTools` lost `ship` entirely — the split has started.
`Move.shippable` is the single gate, `stripTicketPrefix` became `model/ReviewRequestTitle`, and a per-task
in-flight guard stops a double click from pushing twice.
Two deliberate limits, both worth keeping in mind before "improving" them:
- a review-round commit message is mechanical (`<task> address review comments`); the backend cannot describe
  a fix it did not make, and inventing prose is worse than being plain;
- posting the drafted replies is still the agent's, because a reply needs the thread it answers and
  `ReviewFacts.comments` carries formatted strings, not discussion ids. Extending the sweep to carry ids is
  what would finish this — then `CodeHost` could post them and the agent would be out of `ship` completely.

### Superseded — the original argument for moving ship into the backend
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

### Prove the pluggable seams with a second implementation each
`AgentRuntime` and `TerminalDriver` now have two implementations each (claude + codex, kitty + warp). Adding
Codex is what MOVED provisioning into the seam — proof the critique below was right: an interface with one
implementation had quietly left `.mcp.json`, `.claude/settings.local.json` and the word "Claude" sitting in
`OrchestratorTools`.
Still single-implementation, so still unproven: `UserNotifier` (macos only), `EditorDriver` (one CLI driver),
`CodeHost` (GitLab only). The Linux port below is the natural second implementation for the first two.
There is now a THIRD implementation, `StubAgentRuntime` (`orchestrator.agent=stub`), which the e2e matrix runs
on: `launchCommand` runs a configured script (or `true`), `wireAgent` writes nothing — and that emptiness is an
assertion, since a Claude-shaped file in a stub worktree means something outside the runtime put it there.
One follow-up the Codex runtime left behind: a Codex worktree gets jagt's MCP proxy but NOT the human's own
servers (its `CODEX_HOME` points at the worktree, so `~/.codex/config.toml` is not read). Fine for the code,
but such an agent cannot post review replies itself — which stops mattering once `ship` moves into the
backend (step 3).

### `OrchestratorTools` is a god-facade and keeps growing
~1000 lines and ELEVEN injected collaborators (the `AgentRuntime` arrived with the Codex runtime): task
lifecycle (`initializeTask`/`removeTask`/`resumeTask`), worktree file copying (IDE files, local files), git
operations (`deployTask`, `pruneBranches`), agent plumbing (tmux windows, status updates) and the MCP-facing
surface. Every new command lands here because it already has every dependency — `prune` did exactly that.
The agent-specific half of provisioning has left (it lives in `AgentRuntime` now), and the class did not
shrink much: the concerns are additive, so this entry is unaffected.
The tell is in the tests: each one constructs it with eleven arguments, so `OrchestratorToolsTest` is the
heaviest file in the suite — and adding that eleventh argument meant a mechanical edit of 31 call sites, which
is the cost of not having split it yet. Split by concern (task lifecycle / worktree provisioning / repo ops), keep
`OrchestratorTools` as the thin MCP-facing facade that delegates. Do it BEFORE the next command is added, and
expect the test setups to shrink to two or three collaborators each — that shrinkage is the proof it worked.

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
DONE for a configured host (2026-08-13, see the roadmap notes): with `orchestrator.code-host.type` set, a tick
spends no model call at all. Without one it still spends a headless `claude -p` per tick, which is why
`autoReview.enabled` keeps defaulting to `false` — the guard belongs to the expensive path, not to polling
itself, so it can be flipped once a host is wired.

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
- the eleven statuses collapse into six rail steps a human reads at a glance (REVIEW = the human reading the
  diff, which happens BEFORE `ship`; CHECK = the pipeline on the pushed branch):

```
BUILD ──▶    REVIEW ──▶ CHECK ──▶    READY ──▶  DEPLOY ──▶        DONE
NEW          REVIEW_    SHIPPING     REVIEWED   DEPLOYED          DONE
IN_PROGRESS  PENDING    CI_POLLING   APPROVED   DEPLOY_CONFLICT
                        CI_FAILED
🤖 agent     👤 you     🤖/⚙️ CI     👤 you     👤 you            —
```

Keep `TaskStatus` as the persisted SSOT — `Phase` is a projection for humans, not a second state machine.

### Local web UI (mouse-driven), TUI stays as the fallback
The CLI dashboard is fine as a monitor and bad as a control surface: no clicking, no per-task actions, no
timeline, no cost. The backend is already Spring Boot Web on 8290 with `/state`, `/status` and `/stats`
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
link, the drafted-replies indicator, and buttons = the legal actions (`ide`, `ship`, `review` — `sweep`
after the rename below, `deploy`, `done`, `focus`). Header: how many tasks are waiting on YOU, and today's master-side token cost (`stats`).

Phase 2, only if the basic UI proves itself: embed the agent terminal instead of switching windows —
`ttyd -W tmux attach -t jagt` in an iframe makes `focus` a click in the browser. That is a new install
requirement, so it goes into README's Prerequisites table (never install silently).

Do NOT fork the rendering logic: extract one `TaskView` projection consumed by the TUI, the `/status` text
and the JSON alike. And keep the TUI — it is covered by the layout smoke test and is the fallback when
there is no browser.
Rejected alternatives: an IntelliJ plugin (months of work, and it would bind the UI to one editor against
the pluggable-by-design invariant); Electron/native (same cost, more of it); Lanterna mouse support (it
does have `MouseAction`, but clicking inside an ASCII table treats the symptom, not the diagnosis above).

### ~~Status-transition history in `state.json`~~ DONE 2026-08-13
`TaskState.history` is an append-only `[{status, at}]` written by the same `withStatus` path that stamps the
timestamp, capped at the last 50 (the file is rewritten on every MCP call). Two rules make it useful rather
than noisy: a KEEP-ALIVE records nothing (same status → no entry, or four real transitions would drown in
hundreds of identical rows), and a task starts its history at the status it was CREATED with, so "how long did
it sit in NEW" has an answer. `TaskState.statusSince()` is what both surfaces show — NOT
`lastActiveTimestamp`, which a keep-alive bumps, making an hour-old status look fresh.
Still open, and now cheap: cycle-time statistics over the history ("this ticket spent 6 h waiting on me",
"review rounds average 3"), which is a `stats`-shaped question, not a card-shaped one.

### ~~The drafted review replies are invisible until you go looking for them~~ DONE 2026-08-13
`TaskViews` stats the worktree for `review_replies.md` and puts a `draftedReplies` flag on the projection, so
both surfaces announce it: a console detail line that names the file and the `ide <alias>` that opens it, and a
card badge on the board. Presence, deliberately not a COUNT — the agent's brief prescribes no per-comment
marker, so any number would be a guess dressed as a fact.
Still open: the NOTIFICATION path says nothing about drafts (`UserNotifier` fires on the REVIEW_PENDING
transition, which is exactly when they appear), and `postReviewReplies`/`reviewReplyAuthors` remain invisible
until a `ship` acts on them.

### ~~Repaint the TUI on state change, not only on the timer~~ DONE 2026-08-13
`MasterShell` subscribes to `StateService.onChange` — the SAME event the board's SSE uses — and the listener
only raises a flag the render loop consumes (Lanterna's screen belongs to the UI thread; the listener runs on
whichever thread served the agent's MCP call). The periodic tick stays for the relative "ACTIVE" clock.
Pinned by `scripts/tui-push-repaint-smoke.sh`: refresh set to 60s, a status pushed through `POST /mcp`, and the
screen asserted to show it within seconds — so only the event can explain the repaint (verified RED by
deleting the listener).

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
DONE 2026-08-13: `WorktreeOrphanScanner` reports the leftovers (`<task>-<projectKey>` and an abandoned
`<task>-deploy`) with the number of copied secret files still in each — one startup ping, details at
`GET /orphans`. It deletes nothing: an orphan can hold uncommitted work.
Two decisions still open, both about the copying itself:
- should the default set be narrower than "any `*.pem`"? Today one config key covers .env, keys, certs and
  keystores for every project.
- `**/.env` does NOT match a root-level `.env` — Java's glob needs a directory component. A repo whose `.env`
  sits at the top level silently gets nothing copied, and the app then fails to start in the worktree for a
  reason that looks like anything but a glob. Either document it in `config.json.dist` or ship
  `["**/.env", ".env"]` as the default.

## Testing & portability

### Generic wording for the GitLab-leaning internal labels (low priority)
`mrUrl` / "MR" / `CI_POLLING` are GitLab-flavoured INTERNAL names — fine as-is, but user-facing text could
say "review request" / "pipeline or checks" generically. Not worth a churny rename until a non-GitLab host
is actually wired. (The invariant itself — never hardcode a tracker or code host — lives in CLAUDE.md.)

### Run the build on Linux — the drivers are in, the RUNNER is not
DONE 2026-08-13, the driver half: `LibNotifyNotifier` (`notify-send`), `LinuxKittyTerminalDriver`, and the
JetBrains config path is no longer macOS-only (`~/.config/JetBrains` is probed too, which is what would have
left a dead recent-projects entry per task on Linux). Selection is `orchestrator.platform=linux`, and
`LinuxProfileContextTest` boots that profile so a condition typo fails in CI rather than on a desktop.
Two findings worth keeping: driving kitty needed NO Linux-specific code at all (one shared
`AbstractKittyTerminalDriver`, two hooks), and the editor needed no new class — only the config path fixed and
`orchestrator.editor-command` pointed at `idea`/`code`.

What is still missing is the part that cannot be faked from macOS: nobody has run `./gradlew build` or the jar
on a real Linux box, so "it wires" is all that is proven. Do that on a Linux runner (Java 25, Node, tmux, git,
kitty, libnotify), then check the three things a unit test cannot see — does `notify-send` actually raise a
banner under the session bus, does `kitty @ focus-window` reach the window with the WM in charge of stacking,
and does the `pkill -f <socket>` viewer close behave the same. `WarpTerminalDriver` remains macOS-only (URI
scheme + AppleScript) and is not part of this.

### Automated end-to-end test harness across all config combinations, with a deterministic oracle
Goal: one automated suite that exercises the WHOLE task flow (create worktree → provision → launch →
talk over MCP → ship/review/deploy/done) across the full matrix of swappable pieces and config flags, and
asserts a **deterministic expected result** for each combination — so any regression in any combo is caught
without hand-testing.

SKELETON LANDED 2026-08-13 — `./gradlew e2eTest` (own source set `src/e2e/java`, out of `test`/`check`):
`TaskFlowCase.matrix()` × `TaskFlowMatrixTest` runs the CREATE→PROVISION→LAUNCH→TEARDOWN half over the real
git/tmux stack with `orchestrator.agent=stub`, 4 combinations (viewMode × autoReview), asserting worktree
contents, the per-agent provisioning absence, `TaskStatus`, the autoReview flag, and that `done` removes the
worktree while KEEPING the branch. `E2eWorkspace` is the throwaway world (bare origin + clone + config.json +
prefix-killed tmux sessions).
What the skeleton does NOT cover yet, in the order it is worth adding:
1. `ship`/`review`/`deploy`/`resume` — each needs a fake `CodeHost` bean (now trivial: the seam exists) plus a
   stub script that reports statuses back over `POST /mcp`. That is where the oracle gets interesting: status
   transitions, `state.json` history, the drafted-replies relay.
2. the remaining config flags (`postReviewReplies`, `reviewReplyAuthors`, branch strategy fresh/resume, plan
   mode) — data rows once the flow above is scripted.
3. the real driver combinations (`terminal`, `platform`, `editor-command`), which today are Mockito doubles: a
   GUI cannot be asserted, so those need the Linux/macOS driver comparison below, not more rows here.

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
