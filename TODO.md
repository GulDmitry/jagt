# jawo — TODO / future ideas

Backlog of ideas, not commitments. Newest thinking at the top of each section.

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

- tmux status bar styled as clickable job "tabs" (alias + status, active highlighted) so `shared`
  viewMode reads like native tabs without Warp's unclosable-tab limitation.

## Docs / clarity

- The `review` command is confusing (see below) — either merge it into the auto-poll loop above or
  rename it. Right now `ci` and `review` do the SAME full MR sweep; two names for one action is the
  confusion. Likely resolution: once auto-poll lands, drop both as manual commands; keep a single
  manual `sweep <ticket>` as the "check now" escape hatch.
