# jagt — TODO

- A session and the Master get a boundary for what is not task work: no command that harms the machine beyond the
  task (an `rm -rf` outside the worktree, a system service, a disk), while a cluster, a browser or a staging UI the
  task needs stay open to them. Today only the agent CLI's own permission rules stand there.
- Nothing jagt writes may grow unbounded: each file beside `state.json`, `jagt-master.log`, `artifacts/` and the
  worktrees' own files gets a ceiling and what folds, archives or refuses past it. `finished.jsonl` only warns.
