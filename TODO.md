# jagt — TODO

- A production signal could become a task with no token in jagt: an alert (Sentry, an on-call tool) opens a tracker
  item with the label the configured `TrackerWorkflow` reads as ready, and `IntakeJob` opens the task as it does
  today. Open: whether a task that starts as a root-cause read needs a flow `IntakeJob` lacks; settles roadmap §2
  "Off production" if it holds, so it takes a `docs/decisions/` record.
- README says what jagt does, never why to choose it over another orchestrator: gather the differentiators into one
  section README links to, each naming the spec or decision proving it and the peer lacking it. Peers are the
  market-review sources in `CLAUDE.local.md`, not compared yet. Found so far:
  - Moves are the machine's: one compiler-checked `FlowRules` table owns every status and action; a model only judges (0006).
  - A validated protocol: a session reports through a schema-declared MCP message, every violation in one refusal,
    typed validation, business, permission or transient (`protocol`).
  - Bounded retries: a paid read gets three spaced attempts under a budget, each told what the last got wrong, then a
    person, never a guess (`RetryPolicy`, `TicketRead`).
  - Spend seen, never gated: subscription windows and paid usage read through the CLI's own login, one banner per
    threshold; `stats` and `finished` split each task's time and cost (`usage-limits`, 0024, 0033).
  - Isolation: one task, one worktree, branch and request; the base branch read-only; only `deploy` and `revert`
    write a shared branch, neither rewriting history (0005, `git`).
  - Guards outside the project: per-session push hooks under `.jagt/`, `ToolGate` refusing a line, refusals counted
    per task; nothing written into tracked files (0004, `guards`).
  - A session writes only inside its task, under the CLI's OS sandbox; every host stays reachable (0044).
  - Nothing it writes grows unbounded: each store has a ceiling and packs, rolls or refuses past it (0045).
  - Git with the human's credentials runs only the config and hooks the human trusted (0040, `git-trust`).
  - The Master: a per-role review panel, `judge` by default; `act` ships and deploys, stops itself after three bad
    endings, and the human's word overrules it (0010, 0018, 0043, `master`).
  - Acceptance probes a plan declares, run by jagt at VERIFYING, not by the session (0041).
  - A hand-back without a strict-valid OpenSpec change is refused (`spec-changes`).
  - Vendor-neutral: any agent CLI behind a seam; tracker and code host read through the human's own MCP; jagt holds
    no token (0003, 0007, 0020).
  - A restart loses nothing: sessions live in tmux, the state is one JSON file.
  - Unattended work only reads and drafts; tracker intake and close are off by default (`unattended-work`).
  - Setup is one copied file; a start refuses offline with every problem at once, each naming its key (`startup`).
