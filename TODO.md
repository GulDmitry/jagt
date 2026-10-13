# jagt — TODO

- A session and the Master get a boundary for what is not task work: no command that harms the machine beyond the
  task (an `rm -rf` outside the worktree, a system service, a disk), while a cluster, a browser or a staging UI the
  task needs stay open to them. Today only the agent CLI's own permission rules stand there.
- The Master's roles start in parallel, so none reads the round another wrote to the cache: of 739 headless runs 21
  read past the common prefix (0009 assumes they all do). Starting one role and the rest once its first call
  returned saves ~4 × 45k written per panel, at the cost of that first call's latency.
