# jagt — TODO / future ideas

Backlog of ideas, not commitments. Newest thinking at the top of each section.

## Roadmap — decided order (review of 2026-08-12)

In dependency order; each step is detailed in its section below.

| # | step | why it earns its place | est. |
|---|------|------------------------|------|
| 1 | Lean headless-assistant context (`assistant.mcpServers` + `--strict-mcp-config`) | ~10x fewer tokens on every `do`/sweep, no design risk | 0.5 d |
| 2 | Per-task token accounting in `state.json` + dashboard | an unmeasured cost cannot be optimized | 0.5 d |
| 3 | `Move`/`Phase`/`Owner`/`Action` instead of the next-move String | fundament for BOTH the UI and gate validation | 1 d |
| 4 | `CodeHost` REST — review sweep first, then MR create/update | kills the dominant token spend + the "is it approved?" judgement flake | 2-3 d |
| 5 | `ship` = backend commit + push | kills the permission-classifier stall class; `SHIPPING` stops hanging | 1-2 d |
| 6 | Local web UI (SSE + `/api` + static kanban) | mouse-driven, phase-legible, cost visible | 3-5 d |
| 7 | Status-transition history in `state.json` | "which steps happened, how long did review take" | 0.5 d |
| 8 | NL fallback as a command palette (tier 2 of two-tier dispatch) | flexibility, off the hot path | 1-2 d |

Steps 1-3 are independent and pay off immediately. 4 and 5 are what move the remaining mechanics out of the
LLM; 3 is a prerequisite for 6.

## Architecture

### Cost: the headless assistant inherits the human's ENTIRE MCP surface — the measured hot-spot
`HeadlessClaudeAssistant` runs with `--setting-sources user,project,local` (the default in
`AssistantProperties`), so the child loads EVERY user-level MCP server and plugin the human has installed.
On a realistic setup that is 300-400 tool schemas (code host ~130, tracker ~80, IDE ~90, plus plugin packs)
— order-of-magnitude 30-80k input tokens per call before the prompt even starts. It is paid on:
- `readTicket` — once per `do`,
- `readMergeRequest` — once per `resume`,
- `readReview` — once per auto-review POLL. `AutoReviewCadence` ramps 10→60 min across a 24 h window ⇒
  ~40 polls per review request ⇒ ~2M input tokens per MR, for "what is the pipeline status and are there
  unresolved comments". This dwarfs everything else on the master side.

Fix: pass ONLY the servers the read actually needs:
`claude -p --strict-mcp-config --mcp-config <generated file> --setting-sources project --model haiku`.
The server names CANNOT be guessed — jagt does not know whether the tracker is Jira or Linear, the host
GitLab or GitHub, or what the human named them. So they are CONFIG: a new `assistant.mcpServers: ["…"]`
key listing the MCP server names to pass through, and jagt writes the minimal `--mcp-config` file at call
time by copying those entries out of the human's own MCP config. Empty list = today's behaviour (inherit
everything), so an unconfigured install keeps working; document the key in README's Configuration table.
`--setting-sources project` additionally drops the global CLAUDE.md, skills and output styles the read has
no use for.

Side benefit beyond cost: the assistant's context becomes a CONFIGURED contract instead of "whatever the
human happens to have installed this week" — same call, same tools tomorrow. That is a determinism win too,
and it makes the token number from step 2 comparable across runs.

### Cost: per-task token accounting (measure before optimizing)
Spend is invisible today, so every cost decision is a guess. `claude -p --output-format json` returns a
`usage` block next to the result — `HeadlessClaudeAssistant.ask` should read it and accumulate per task in
`state.json`, surfaced as a dashboard column plus a session total in the header. Verify first how
`--output-format json` composes with `--json-schema` (the answer likely moves into `.result`, which then
needs one more parse step) — that check IS the first task.
Caveat to state in the UI rather than paper over: sub-agent spend lives in the agent's own session and is
NOT visible to jagt, so the number is master-side cost, not a task total.

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
headless read; measure the token drop with step 2's counter — that validates the whole "thicker app →
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

## Automation

### Make the auto-review poll free
Every auto-review tick spends a headless `claude -p` (the dominant cost measured above) on a mechanical
read. That is exactly what the `CodeHost` REST sweep removes; until it lands, `autoReview.enabled`
defaulting to `false` is the cost guard.

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
`done`, `focus`). Header: how many tasks are waiting on YOU, and today's master-side token cost (step 2).

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

## Testing & portability

### Generic wording for the GitLab-leaning internal labels (low priority)
`mrUrl` / "MR" / `CI_POLLING` are GitLab-flavoured INTERNAL names — fine as-is, but user-facing text could
say "review request" / "pipeline or checks" generically. Not worth a churny rename until a non-GitLab host
is actually wired. (The invariant itself — never hardcode a tracker or code host — lives in CLAUDE.md.)

### Verify the build on Linux
Confirm `./gradlew build` and the runnable jar work on Linux (Java 25, Node, tmux, git present). The core
is OS-neutral; the only OS-specific code is behind the platform strategies (`UserNotifier`/`TerminalDriver`/
`EditorDriver`). First milestone: backend boots + the deterministic tests pass on a Linux runner, even
before Linux impls of the three drivers exist (they can no-op / fail-soft until then).

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
