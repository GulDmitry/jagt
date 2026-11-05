# 0139 — Every suite ends within one ceiling

[← decisions](README.md) · rule: [`testing`](../rules/testing.md) · plugin: `build-logic/src/main/groovy/jagt.conventions.gradle`

## Status

Accepted 2026-11-05 by the owner's order: "development of jagt has to be fast".

## Context

- A local-model QA eval ran 27 minutes for 2 of 12 cases, and nothing stopped it.
- Measured 2026-11-05 on a 10-core Mac, `--rerun-tasks --no-daemon`: `test` 66 s, `boardTest` 85 s, `e2eTest`
  168 s, `roleEval -Prole=master` about 7 min (21 cases), `promptEval` under 1 min.
- The local-model QA eval: 32 min for 10 cases, its time spent in loops the read repeats, not in the model.
- CI's `pitest` on a push's diff: 47 min on 2026-11-05 (5,428 mutations, `usecase` 2,813 s, 30 minion timeouts).
- `pitestDiff` on one commit's diff: about 130 s for 1 and for 8 changed main files, most of it coverage analysis.

## Decision

- `suiteCeilingMinutes` in `gradle.properties` is the one ceiling, 10: every `Test` task times out at it.
- A suite over it is a defect to speed up, never a raised number.
- An investigation (a measurement, a model comparison, a spike, a local-model eval) states its expected length
  before it starts as `-PrunMinutes=<n>`, which replaces the ceiling for that run only.
- The regular flow (commit gate, batch-end suites, release gate) never passes `-PrunMinutes`.
- Excluded until sped up: `pitest` on a push's diff (the cause above); it still reports its seconds.
- `suite-stats.jsonl` keeps each executed `Test` and `pitest` task's seconds; a `Test` task slower than
  `suiteSlowdownFactor` times the median of its last `suiteTrendRuns` runs under the same command is named at the
  build's end, never failing it; `pitest` has no trend, its work is the diff's size.
- `pitest` runs jqwik at 100 tries: `TaskReducer`'s 95 mutants took 4 min 17 s at 1,000 and 30 s at 100, every
  mutant detected at both.
- A changed `:core` type is mutated once, in `:usecase`, with `:core`'s tests on its classpath: the 2026-11-05
  push diff took 11 min 40 s instead of 14 min 41 s, the same 3,015 of 3,288 mutants detected.
- The ArchUnit rule tests share one import of main, `MainClasses.CODE`: each class took 19–27 s re-importing,
  about 4 s sharing it; the root `:test` task 16 s to 9 s.
- `e2eTest` runs its classes in parallel forks, each naming its tmux sessions by its pid: 168 s to 90 s, bounded by
  `MasterModeFlowTest`.

## Rejected

- A constant per task: one more number per suite, each raised on its own.
- No ceiling on investigations: a run nobody bounded is the one that ran 27 minutes.
- `pitest`'s incremental history: 1.30 refuses it without arcmutate's paid history plugin.
- `threads` split across the concurrent `pitest` tasks: `:usecase` alone is the critical path.

## Reopen when

- A suite of the regular flow cannot be brought under the ceiling without dropping a case.
