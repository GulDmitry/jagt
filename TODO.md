# jagt — TODO

- A session and the Master get a boundary for what is not task work: no command that harms the machine beyond the
  task (an `rm -rf` outside the worktree, a system service, a disk), while a cluster, a browser or a staging UI the
  task needs stay open to them. Today only the agent CLI's own permission rules stand there.
- Nothing jagt writes may grow unbounded: each file beside `state.json`, `jagt-master.log`, `artifacts/` and the
  worktrees' own files gets a ceiling and what folds, archives or refuses past it. `finished.jsonl` only warns.

## To triage: ideas from OpenJCockpit and the-metafactory (2026-10-14)

- A digest of git config and hooks taken when known clean, checked before ship, deploy, revert and a Master read;
  a mismatch parks the task. Closes the two config rows open in `guards.md` (ranger `git-trust.ts`).
- The not-ready loop has no cap: a finding repeating an earlier round's on the same file is marked a repeat, and N
  rounds of only repeats become your question. Replay `master-review.md` first (sage `verdict/convergence.ts`).
- A brief role gets an `applies` path-glob column; a diff matching none skips its heavy read
  (sage `lenses/applicability.ts`).
- `plan.md` declares typed acceptance probes (exit code, ref, artifact) that `VerifyJob` runs, so "never ran the
  ticket's check" is a fact, not the Master's judgement (soma `docs/work-graph.md`).
- Three Master-moved tasks in a row ending crashed, silent, conflicted or reverted drop `act` to `judge` until you
  restore it; touches 0018 (ranger dead-man switch).
- Unattended work keeps the Mac awake: `caffeinate -i -w <pid>` behind a port in `adapter/macos/` (ranger `awake.ts`).
- A disagreement `MasterRecord` sees offers a drafted `master-lesson` in the `master` report, never applied unasked.
- A test pins that the Master's `do` never opens a task from its own unasked-scope finding
  (ranger self-amplification ban).
- `master.humanPaths` globs (auth, secrets, retention) keep deploy with you when the diff touches one (OpenJCockpit).
- Guard refusals counted in the `finished` report, so a gap record reopens on numbers (OpenJCockpit policy audit).
- One reflective test that each record's `withX` changes only its own component
  (OpenJCockpit `WorkflowDefinitionWitherTest`).
