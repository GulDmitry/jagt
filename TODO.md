# jagt — TODO / future ideas

Backlog of ideas, not commitments. Newest thinking at the top of each section.

## Architecture

### Replace the persistent Master Claude session with a deterministic in-process REPL
The Master does two separable jobs: (1) routing + dashboard — parse terse commands, call tools, render
state; already ~fully deterministic in Java (`NextMove`, `DashboardLine`, status validation, aliases),
the LLM adds nothing here; (2) language + external systems — read the Jira ticket, distill instructions,
GitLab MR/CI ops. Only (2) actually needs Claude (it's the Jira/GitLab bridge; the backend holds no
tokens by design).

Plan: kill the persistent Master chat. Add a **Spring Shell** command layer INSIDE the backend (same
JVM) — commands (`do/ship/review/focus/deploy/done/status`) parse via grammar and call `OrchestratorTools`
directly: no MCP round-trip, no tokens, no drift, instant. NOT JShell (separate process, Java syntax, no
Spring context). Sub-agents keep the MCP server + `mcp_client.js`; only the Master's MCP path collapses
into direct calls.

Fork for job (2) — where external ops live once the Master isn't Claude:
- (A) delegate to sub-agents — they already have Jira/GitLab MCP; `do PAN-123` spawns the agent with the
  ticket id, it reads Jira + plans itself (distillation step disappears); MR/CI during ship/review is the
  agent's own work. Master-REPL never touches the outside. Most aligned with "backend talks to nothing".
- (B) headless `claude -p` for master-scoped ops — stateless subprocess instead of a drifting chat.
- (C) backend gets its own Jira/GitLab clients + tokens — reverses an earlier decision; rejected.

Leaning (A): then the Master side needs ZERO LLM calls. A local "vectorization-class" model does NOT fit
here — command parsing needs no model (grammar), and ticket distillation / agentic tool-calling need a
capable model, not embeddings.

**Decided direction — two-tier dispatch (balance of speed / flexibility / cost).** Keep NL flexibility
but off the hot path, so a plain command never waits on a model:
1. Parse input as a grammar command → execute deterministically via `OrchestratorTools` (instant, 0
   tokens, 0 network). This is ~95% of interactions (`ship p1`, `focus s2`, `status`, `done`).
2. No grammar match / free text ("залей ту задачу с логином") → hand the raw text to a LEAN headless
   Claude that maps it to a grammar command → VALIDATE (`SAFE_ID`, project mapping) → execute. Tokens +
   latency only here, only when flexibility is actually used. The LLM never executes directly — it only
   proposes; deterministic code executes after the gate (asymmetric-failure-cost rule).

Lean headless Claude recipe (kills the token/latency bloat from the user's global MCPs + rules):
```
claude -p --model haiku \
  --strict-mcp-config --mcp-config /dev/null \   # ignore ALL global MCP servers (their schemas = tokens)
  --setting-sources project \                    # skip global CLAUDE.md / plugins / rules
  --append-system-prompt "<grammar + current task list>"
```
Prefer headless Haiku over a resident local model: a 3-7B local model adds 4-8 GB RAM (the machine
already swaps — see the jdtls incident); headless `-p` holds nothing resident, needs no infra, and with a
stripped context is nearly as cheap. Revisit a local model only if API cost ever dominates.

External ops (Jira/GitLab): tokens-in-backend is now allowed (env vars), so do these as deterministic
REST clients in the backend (0 model) — or leave them in the sub-agent. The NL fallback only maps
text→command; it never reaches outside. Ticket distillation moves INTO the sub-agent (pass the raw ticket
to `task_context.md`; the agent reads + plans itself).

Tech: **Spring Shell** in the backend (same JVM, direct calls) — NOT JShell (separate process, Java
syntax, no Spring context). Next step: skeleton = Spring Shell parser + a `HeadlessClaude` wrapper around
the flags above.

## Automation

### Auto-poll the review request after `ship` — DECIDED (auto-review, windowed, escalating cadence)
Today the human must run `ci`/`review` to pull pipeline status + MR comments. Goal: after `ship`, the
system watches the MR on its own within a bounded time window and only pings the human when input is
actually needed. `ci`/`review` stop being manual commands — they become the poller's internal steps
(keep a single manual `sweep <ticket>` as the "check now" escape hatch, see Docs/clarity below).

Where it lives (decided — TODO option (b), keeps the backend integration-free): a new
`AutoReviewScheduler` (`@Scheduled(fixedRate=60_000)`, modelled on `WatchdogService`). The backend
still talks to NO external system — the scheduler orchestrates and delegates the outside read to
`MasterAssistant` (headless `claude -p`, inherits the human's own code-host MCP). Each poll spawns one
headless process (tokens); the cadence backoff below is the direct cost lever.

HARD RULE — code review is never fully automated. Auto-review only READS and DRAFTS; posting is always
human-gated. Every auto-round hands the human two artifacts: the local diff (agent's fixes) and
`review_replies.md` (what the LLM intends to reply to each thread). The human does `ide <alias>`,
inspects BOTH the code and the drafted replies, edits, and only an explicit `ship` posts the round. The
auto-loop never `ship`/`deploy`/pushes/posts on its own. This is the "human in the loop" invariant —
already enforced in `reviewTask()` (relay brief → agent fixes locally + drafts, no push) — do not erode.

State (per-task, in `state.json` — NOT config; it is per-MR data):
- `mrCreatedAt` — set on `ship`/`resume`; the start of the auto-review window.
- `lastPolledAt` — to decide "is it time to poll" against the computed interval.
- `autoReview` — per-task on/off, defaulting from config (lets one task opt out).

Config (new `autoReview` section, same value-record shape as the others — `defaults()`/`withX`/
`*OrDefault`; document every key in README's Configuration table):
```
"autoReview": { "enabled": true, "windowHours": 24, "minIntervalMinutes": 10, "maxIntervalMinutes": 60 }
```

Cadence — a PURE function `pollInterval(elapsed)` (no attempt counter stored; interval derived from
`now - mrCreatedAt`). DECIDED: LINEAR ramp min→max across the window, capped at max (= hourly). After the
window: return null → STOP polling + one `notify_user` "auto-review window elapsed — sweep manually".
Pure fn ⇒ unit-test monotonicity + bounds + null-after-window trivially.
```java
Duration pollInterval(Duration elapsed) {                       // null = stop
    if (elapsed.compareTo(window) > 0) return null;
    double f = (double) elapsed.toMinutes() / window.toMinutes();          // 0..1
    long m = Math.round(minMinutes + (maxMinutes - minMinutes) * f);       // linear 10→60
    return Duration.ofMinutes(Math.min(maxMinutes, m));
}
```
Scheduler tick: for each task in CI_POLLING with `autoReview` + `mrUrl`, if
`now - lastPolledAt >= pollInterval(now - mrCreatedAt)` → poll (skip if a poll is already in-flight for
that task; `readReview` runs up to 6 min, must not overlap the 60s tick — run on a bounded executor,
one in-flight per task).

Per-poll flow (extend `ReviewFacts` with `boolean approved` + add `approved` to `REVIEW_SCHEMA`):
1. `assistant.readReview(mrUrl)`.
2. approved && pipeline green && no unresolved → status **APPROVED** (new enum value) +
   `notify_user` "ready: deploy/done <alias>".
3. unresolved comments (or pipeline failed) → run the existing `reviewTask()` logic: relay ONE
   consolidated brief via `task_context.md`, agent fixes LOCALLY + drafts replies in `review_replies.md`,
   nothing pushed/posted → status REVIEW_PENDING + `notify_user` "your move: ide <alias>".
4. Debounce: one ping per STATE CHANGE, not per poll — track last-notified state per task (like
   `WatchdogService.lastAlertAt`).

New status APPROVED (decided): distinct from REVIEWED — APPROVED means a human actually approved the MR,
REVIEWED just means "no unresolved + green". Touches the enum, `DashboardRenderer`, `NextMove`, tests.

## UX

### Live-refresh the dashboard in place (don't scroll a new copy each time)
Today the dashboard only redraws after a command. Want it to refresh on its own (~10s, or on state
change) so "ACTIVE 5m ago" and statuses stay current without typing `status` — and crucially redraw IN
PLACE (fixed region, terminal doesn't scroll down), not append a fresh copy each tick.

Design:
- The shell blocks on JLine `reader.readLine("jagt> ")`, so a background repaint must not disturb the
  typed buffer. `reader.printAbove(...)` does that but SCROLLS (each tick appends above the prompt) — not
  what we want.
- True in-place: JLine has a `Status` region (`Status.getStatus(terminal)`, multi-line, redrawn in a
  fixed block at the bottom) — render the dashboard into that instead of the scrollback. Alternative:
  reserve a block + ANSI cursor save/restore + clear-to-EOL. Prefer `Status` (handles resize/wrap).
- Trigger: prefer EVENT-DRIVEN over a 10s poll — local dashboard state is `state.json`, mutated in-process
  by `StateService` (agent MCP calls land in the backend). A `StateService` change listener can signal the
  shell to repaint immediately (0 latency, no busy poll); keep a slow ~10s tick only to refresh the
  relative "ACTIVE Xm ago" clock. Debounce coalesced writes.
- Keep command output in the scrollback (normal `println`); only the dashboard block lives in the fixed
  region. Ctrl-D / no-TTY path must degrade to the current print-once behavior.

### Move off Warp — persistent typing lag + no tab/split control API
Typing in a tmux window through Warp is sluggish ("like jelly", noticeable input delay), and Warp has no
programmatic surface for tabs/splits/windows (URI scheme only — verified). Both point to a different host
terminal. Candidates with a REAL remote-control API AND fast GPU rendering:
- **kitty** — `kitty @ launch --type=tab|window`, splits, focus, close; fully scriptable; very fast.
- **WezTerm** — `wezterm cli spawn / split-pane / list`, Lua config; GPU render.
- **iTerm2** — Python + AppleScript API; full session/tab/split control.
A terminal that multiplexes natively (kitty/WezTerm) could let us DROP tmux for the viewer entirely — that
removes the Warp→tmux double-render which is the actual source of the lag, and gives closable tabs (Warp's
big limitation). Fits the `TerminalDriver` strategy: a new impl + config, no core changes. Evaluate render
latency and the tab-control API of each; kitty is the front-runner (simplest control protocol).

- tmux status bar styled as clickable job "tabs" (alias + status, active highlighted) so `shared`
  viewMode reads like native tabs without Warp's unclosable-tab limitation.

## Docs / clarity

- The `review` command is confusing (see below) — either merge it into the auto-poll loop above or
  rename it. Right now `ci` and `review` do the SAME full MR sweep; two names for one action is the
  confusion. Likely resolution: once auto-poll lands, drop both as manual commands; keep a single
  manual `sweep <ticket>` as the "check now" escape hatch.

## Testing & portability

### Tracker- and VCS-host-agnostic: never hardcode Jira / GitLab
The backend must assume NOTHING about which issue tracker or code host is in play — the ONLY source of
truth is whatever MCP the human's session exposes. The tracker may not be Jira (Linear, GitHub Issues, a
plain URL to anything); the code host may not be GitLab (GitHub PRs, Bitbucket PRs, any `http(s)` git URL).
This is the external-systems dimension of the "PLUGGABLE BY DESIGN" invariant: no `if jira` / `if gitlab`,
no host-specific wording that narrows what an MCP call will accept.

Done: `trackerProject` field + schema/prompt (was `jiraProject`); `readMergeRequest`/`readReview` prompts
now say "the merge/pull request at <url> via the matching code-host MCP"; `master_prompt.md` +
`OrchestratorTools` provisioning text say "your code-host MCP" / "your issue-tracker MCP". Label-based
routing (`projectsMatching`) was already tracker-neutral.

Remaining (low priority): `mrUrl` / "MR" / `CI_POLLING` are GitLab-leaning INTERNAL labels — fine as-is,
but user-facing prompt text could say "review request" / "pipeline or checks" generically. Not worth a
churny rename until a non-GitLab host is actually wired.

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
  branch strategies (fresh / resume), deploy on/off, plan mode, etc.

Design notes:
- Needs a **deterministic oracle**: fake/record-replay the external, non-deterministic pieces — a stub
  `AgentRuntime` that emits scripted MCP calls instead of a real LLM, a throwaway local Git origin, a
  throwaway tmux session, and headless terminal/editor/notifier drivers (no GUI). Then every combo has a
  fixed expected end state (state.json transitions, branches/worktrees created + cleaned, MR/CI mocked).
- Assert on OBSERVABLE state, not timing: final `TaskStatus`, git refs/worktrees present-or-gone, files
  written into the worktree, notifications emitted. Follows the existing smoke-test etiquette (throwaway
  tmux + `ORCHESTRATOR_ROOT` + `--orchestrator.open-warp-window=false`, leave no trace).
- Run the SAME matrix on macOS now and on Linux once its drivers exist — the harness is the portability
  gate: "does combination X behave identically on both OSes?" Think through how to keep the expected-result
  oracle OS-independent (the flow is OS-neutral; only the driver side effects differ).
