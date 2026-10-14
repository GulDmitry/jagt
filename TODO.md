# jagt — TODO

- A session and the Master get a boundary for what is not task work: no command that harms the machine beyond the
  task (an `rm -rf` outside the worktree, a system service, a disk), while a cluster, a browser or a staging UI the
  task needs stay open to them. Today only the agent CLI's own permission rules stand there.
- Nothing jagt writes may grow unbounded: each file beside `state.json`, `jagt-master.log`, `artifacts/` and the
  worktrees' own files gets a ceiling and what folds, archives or refuses past it. `finished.jsonl` only warns.
- A production signal could become a task with no token in jagt: an alert (Sentry, an on-call tool) opens a tracker
  item with the label the configured `TrackerWorkflow` reads as ready, and `IntakeJob` opens the task as it does
  today. Open: whether a task that starts as a root-cause read needs a flow `IntakeJob` lacks; settles roadmap §2
  "Off production" if it holds, so it takes a `docs/decisions/` record.
