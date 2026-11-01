# 0125 — Mutants are judged on the diff, at each commit

[← decisions](README.md) · rule: [`testing`](../rules/testing.md) · plugin: `build-logic/src/main/groovy/jagt.pitest.gradle`

## Status

Accepted 2026-11-01.

## Context

- The refresh judged every survivor of a full `pitest` once a week. On 2026-11-01: 1,141 survivors and no-coverage
  mutants, about 900K agent tokens, 0 real bugs, about 175 missing assertions.
- 782 of the 1,141 (69%) stood on lines written since the refresh before: most of the cost is new code nobody asserted,
  judged by an agent that never saw it written.
- About half were equivalent or pitest artefacts; 170 were core mutants only `:usecase`'s tests reach.
- One class costs 35–40 s of pitest; a six-file diff 222 s.

## Decision

- A commit changing `main` runs `./gradlew pitestDiff -PpitestDiff`: pitest on the changed classes only, listing the
  mutants that survive on a changed line. The session that wrote the lines judges each before the commit.
- Only changed lines are listed, so a mutant judged equivalent never returns until its line changes. Nothing is stored.
- `:usecase`'s run also mutates `:core`; `toString` and `hashCode` are not mutated.
- The refresh and the release review no longer run mutants.

## Rejected

- A file of judged mutants: it goes stale, and a stale verdict hides a gap; git already dates every line.
- Diff per release: one week was 433 commits, and its author has lost the context.
- arcmutate's git-diff analysis: the industry tool for this, but commercial; the line filter is about 60 lines of Groovy.
- Inline suppression (`@DoNotMutate`, comments): a seam in `main` for a test, and comments default to none.

## Reopen when

- A commit's `pitestDiff` routinely takes over 5 minutes.
- A defect lands on a line whose mutant was judged equivalent.
