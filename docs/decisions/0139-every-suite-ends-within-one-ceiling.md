# 0139 — Every suite ends within one ceiling

[← decisions](README.md) · rule: [`testing`](../rules/testing.md) · plugin: `build-logic/src/main/groovy/jagt.conventions.gradle`

## Status

Accepted 2026-11-05, the owner's order: "development of jagt has to be fast"; amended by [0233](0233-a-local-green-predicts-ci.md); soft 2026-11-23.

## Context

- A local-model QA eval ran 27 min for 2 of 12 cases, unstopped, in loops the read repeats.
- Measured 2026-11-05, 10-core Mac, `--rerun-tasks --no-daemon`: `test` 66 s, `boardTest` 85 s, `e2eTest` 168 s,
  `roleEval -Prole=master` about 7 min (21 cases), `promptEval` under 1 min.
- `pitest` in CI on a push's diff: 47 min on 2026-11-05 (5,428 mutations, 30 minion timeouts).

## Decision

- The declared fitness function `suite-minutes` of `fitness.yml` is the one ceiling, 10, a soft gate (owner, 2026-11-23): a run past it
  finishes, its pass or fail counts, and one WARN line names the suite, its minutes, the machine load and the power state;
  a suite over its ceiling is a defect to speed up, never a raised number.
- The hang guard is 3x the ceiling (a 10-minute suite stops at 30): the fitness plugin sets it and names the stop as hung.
- Off CI only: `-Ptimed=false`, set by both CI engines, binds no time row and records no suite time; a job's timeout stops a hang.
- The full gate (`test`, `boardTest`, `e2eTest`, `standTest`, one `--rerun-tasks` run) ends within the same 10 minutes
  and as far under as it can (owner, 2026-11-07); evals and `pitest` aside. Each refresh measures it; over it opens a
  speed-up task, ending in sped up or the owner raising it; never a failed build.
- An investigation (a measurement, a model comparison, a spike, a local-model eval) states its expected length
  before it starts as `-PrunMinutes=<n>`, which replaces the ceiling, and so its guard, for that run only.
- The regular flow (commit gate, batch-end suites, release gate) never passes `-PrunMinutes`; `pitest` on a push's diff is excluded.
- `suite-stats.jsonl` keeps each `Test` and `pitest` task's seconds, load, power state and whether the run was fresh (`--rerun-tasks`).
  Only a fresh `Test` run slower than `suiteSlowdownFactor` times the median of its last `suiteTrendRuns` fresh runs is named as a
  degradation, never failing the build; any other overrun is information. `pitest` has no trend (owner, 2026-11-23).
- A changed `:core` type is mutated once, in `:usecase`, with `:core`'s tests on its classpath: the 2026-11-05
  push diff took 11 min 40 s instead of 14 min 41 s, the same 3,015 of 3,288 mutants detected.
- `pitest` runs jqwik at 100 tries: `TaskReducer`'s 95 mutants, 4 min 17 s at 1,000, 30 s at 100, all detected.
- The ArchUnit rule tests share one import of main, `MainClasses.CODE`: each class took 19–27 s re-importing,
  about 4 s sharing it; the root `:test` task 16 s to 9 s.
- jqwik runs in `:core:propertyTest` and `:usecase:propertyTest`, finalizing their `test`, in `check`: core gate 85 s to 73 s, same 7,258 tests.
- `e2eTest` runs its classes in parallel forks, each naming its tmux sessions by its pid: 168 s to 90 s, bounded by
  `MasterModeFlowTest`: 92 s at load 2 on 2026-11-08, its 115 s at load 9, the same 32 tests.
- Every `Test` task runs jqwik at 50 tries, 200 at the release gate (`-PjqwikTries=200`); `JobsTellOnceProperties` took 154 s at 200 (2026-11-19).
- `pitestDiff` runs only tests named after a changed class: 274 s to 53 s and 300 s to 64 s, survivors equal (2026-11-22).

## Rejected

- A constant per task, or no ceiling on investigations: a run nobody bounded ran 27 minutes.
- `pitest` history (1.30 needs arcmutate's paid plugin, 2026-11-22); a `threads` split: coverage is 95 % of the seconds.

## Reopen when

- A suite of the regular flow cannot be brought under the ceiling without dropping a case.
