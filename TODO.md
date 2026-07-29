# jawo — TODO / future ideas

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

## Automation

### Auto-poll GitLab after `ship` (remove the manual `ci`/`review` step)
Today the human must run `ci`/`review` to pull pipeline status + MR comments. Goal: after `ship`, the
system watches the MR on its own and only pings the human when human input is actually needed.

Desired loop (per task in CI_POLLING):
1. Poll the MR: pipeline status + unresolved review threads (bots like CodeRabbit + humans).
2. Pipeline failed OR new actionable comments → relay ONE consolidated brief to the agent, which fixes
   locally and drafts replies (no push) → set REVIEW_PENDING → `notify_user` "your move: ide <alias>".
3. Pipeline green AND all threads resolved → `notify_user` "ready: deploy/done <alias>".
4. The human's only jobs become: review in IDE, approve (`ship` posts the round), decide deploy/done.
   `ci`/`review` disappear as manual commands — they become the poller's internal steps.

Open questions / design:
- Where does polling live? The backend deliberately talks to NO external systems (no tokens). Options:
  (a) reintroduce a scheduled poller in the backend behind a token (reverses an earlier decision),
  (b) a headless `claude -p` cron job that runs the same GitLab-MCP sweep the Master does now,
  (c) the Master session self-schedules (Monitor/loop) while it stays open.
  Leaning (b): keeps the backend integration-free, reuses the agent's own GitLab MCP, survives Master
  restarts.
- Debounce notifications: one ping per state change, not per poll. Track last-seen pipeline id +
  resolved-thread count per task in state.json.
- Cadence: pipelines take minutes — poll ~60-90s; back off when idle.

## UX

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
