# 0139 — Every suite ends within one ceiling

[← decisions](README.md) · rule: [`testing`](../rules/testing.md) · plugin: `build-logic/src/main/groovy/jagt.conventions.gradle`

## Status

Accepted 2026-11-05 by the owner's order: "development of jagt has to be fast".

## Context

- A local-model QA eval ran 27 min for 2 of 12 cases, unstopped; another 32 min for 10, in loops the read repeats.
- Measured 2026-11-05 on a 10-core Mac, `--rerun-tasks --no-daemon`: `test` 66 s, `boardTest` 85 s, `e2eTest`
  168 s, `roleEval -Prole=master` about 7 min (21 cases), `promptEval` under 1 min.
- `pitest` in CI on a push's diff: 47 min on 2026-11-05 (5,428 mutations, 30 minion timeouts); `pitestDiff` on one
  commit: about 130 s for 1 and for 8 changed main files, most of it coverage analysis.

## Decision

- `suiteCeilingMinutes` in `gradle.properties` is the one ceiling, 10: every `Test` task times out at it.
- A suite over it is a defect to speed up, never a raised number.
- The full gate (`test`, `boardTest`, `e2eTest`, `standTest`, one `--rerun-tasks` run) ends within the same 10 minutes
  and as far under as it can (owner, 2026-11-07); evals and `pitest` aside. Each refresh measures it; over it opens a
  speed-up task, ending in sped up or the owner raising it; never a failed build.
- An investigation (a measurement, a model comparison, a spike, a local-model eval) states its expected length
  before it starts as `-PrunMinutes=<n>`, which replaces the ceiling for that run only.
- The regular flow (commit gate, batch-end suites, release gate) never passes `-PrunMinutes`.
- Excluded until sped up: `pitest` on a push's diff (the cause above); it still reports its seconds.
- `suite-stats.jsonl` keeps each `Test` and `pitest` task's seconds; a `Test` task slower than `suiteSlowdownFactor`
  times the median of its last `suiteTrendRuns` runs is named at the build's end, never failing it; `pitest` has no trend.
- `pitest` runs jqwik at 100 tries: `TaskReducer`'s 95 mutants, 4 min 17 s at 1,000, 30 s at 100, all detected.
- A changed `:core` type is mutated once, in `:usecase`, with `:core`'s tests on its classpath: the 2026-11-05
  push diff took 11 min 40 s instead of 14 min 41 s, the same 3,015 of 3,288 mutants detected.
- The ArchUnit rule tests share one import of main, `MainClasses.CODE`: each class took 19–27 s re-importing,
  about 4 s sharing it; the root `:test` task 16 s to 9 s.
- jqwik runs in `:core:propertyTest`, finalizing `:core:test`, in `check`: gate 85 s to 73 s, same 7,258 tests.
- `e2eTest` runs its classes in parallel forks, each naming its tmux sessions by its pid: 168 s to 90 s, bounded by
  `MasterModeFlowTest`: 92 s at load 2 on 2026-11-08, its 115 s at load 9, the same 32 tests.
- `:core:propertyTest` runs jqwik at 200 tries: `TaskReducerProperties` 105 s to 7 s (2026-11-08, reducer grown).
- `TaskChart.ranTargets` is computed once: each call took 2 s and `FlowCheckTest` made about 15, 31 s to 0.3 s.
- Left: `AgentSessionsTest` `after(n).never()` and `ReplaySessionsTest`'s 500 ms tick need a test-only seam.

## Rejected

- A constant per task, or no ceiling on investigations: a run nobody bounded ran 27 minutes.
- `pitest`'s incremental history: 1.30 refuses it without arcmutate's paid history plugin.
- `threads` split across the concurrent `pitest` tasks: `:usecase` alone is the critical path.

## Reopen when

- A suite of the regular flow cannot be brought under the ceiling without dropping a case.
