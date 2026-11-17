# 0196 — pitestDiff mutates only the changed lines

[← decisions](README.md) · on [0125](0125-mutants-are-judged-on-the-diff-at-each-commit.md),
[0127](0127-mutants-run-with-every-test.md), [0193](0193-the-stand-harness-is-a-module-outside-the-assembly.md)

## Status

Accepted 2026-11-17, owner's choice (JAGT-128).

## Context

- Pitest mutates every line of each changed class; `pitestDiff` then discards every mutant off a changed line.
- Measured 2026-11-17 on a 71-path diff: `pitestDiff` 840 s of the gate's 900 s; `:adapter` 827 s for 282 mutants
  (234 s coverage, 591 s mutation), `:usecase` 604 s for 205 mutants. Modules already run in parallel.

## Decision

- `:mutation-scope` is a Gradle module beside `:stand`: a pitest `MutationInterceptor` of type `FILTER`, registered through
  `META-INF/services`, that keeps a mutant only when its source file and line are in the diff's changed lines.
- `jagt.pitest` writes `changedLines` to `build/pitest-diff/changed-lines.txt`, and for each module `pitestDiff` targets
  adds `+CHANGEDLINES(lines[<file>])` and the module to the `pitest` tool classpath.
- A plain `./gradlew pitest` sets neither: it stays whole-class.
- `pitestDiff`'s own pass over the XML stays, so a changed file with no mutant on a changed line is still named.
- A missing or unreadable lines file fails the run, never keeps every mutant.
- [0176](0176-every-kind-has-a-ceiling.md) ceilings rise with it:
  `gradleModules` 6 to 7, `sourceSets` 25 to 27, `dependencyLinesInBuildGradle` 67 to 74.
- Its checks are the other modules': `:mutation-scope:test` runs with `test`, Checkstyle, Spotless, JaCoCo.

## Rejected

- Arcmutate's git plugin: commercial, and does this same filtering.
- `CLASSLIMIT`: caps mutants per class and drops changed-line ones arbitrarily.
- Sharding a module's pitest: the cost is the mutants run, so shards gain nothing.
- Code in `build-logic/`: it has no test source set, and a filter that decides which mutants are judged needs its own tests.

## Reopens when

- Pitest ships a built-in changed-lines filter.
- The coverage phase, not mutation, is what passes the limit: the filter does not shorten it.
