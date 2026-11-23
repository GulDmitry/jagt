# Test suites

[← testing](testing.md)

| tier ([`ci`](ci.md)) | suites | command | red means |
|---|---|---|---|
| T0 edit loop | targeted tests, mutants on the diff | `./gradlew -q testsFor`, `pitestDiff` | fix before the commit |
| T1 local gate = T2 pull request | `test` (rules, ArchUnit, budgets, jqwik 50 tries, 0.80 line coverage per module), `boardTest`, `e2eTest`, `linuxDriverTest`, `guardsTest`, `standTest -Pmodelfree` on compose, `installTest` | `ci-local.sh`, jobs by name | local: fix before the push; CI: a divergence |
| T3 main | none: publish what T2 proved | `release` job | a publish failure |
| T4 nightly | `standTest -Pmodelfree -Pengine=kind`, repeated and cold runs, full `pitest`, Stryker, jqwik at high tries, install on the real feed, live scans | `nightly.yml` | a task opens |
| T5 weekly and release gate | `standTest` live, `promptEval`, `roleEval -Prole=master`, base images, Scorecard; jqwik at 200 tries (`-PjqwikTries`) | locally: docker, an agent login, tokens | before a minor release |

Windows and macOS install run in T2 only: the one job without a local twin.
GitLab runs compose alone: kind needs a privileged runner. Absolute stand budgets need `-Pbudgets=true` on the idle reference machine.
CI sets no time ceiling; the ceilings below and `suite-minutes` run locally.
A `schedule` run starts late under GitHub load and runs the default branch's latest commit
([events](https://docs.github.com/en/actions/reference/workflows-and-actions/events-that-trigger-workflows),
checked 2026-11-19): the cron sits off the hour and `nightly` logs its delay. `gh workflow run nightly.yml --ref main` starts it on demand.
Times are wall clock, dated in `measurements.md`.

## What each suite proves

| suite | proves | needs | time |
|---|---|---|---|
| `test` | units, rules, properties, mutants on the diff | JDK | ~2 min |
| `boardTest` | the board in Chromium: cards, verbs, tabs, reports | Playwright | ~1.5 min |
| `e2eTest` | a task's flow through real git, tmux and a scripted agent | git, tmux | ~2 min |
| `linuxDriverTest` | the Linux terminal and desktop drivers | a Linux display | unknown |
| `guardsTest` | each contributor guard refuses what it names and passes what it must | shellcheck, bats, jq | unknown |
| `standTest -Pmodelfree` | recorded task flows replayed in jagt's container, and the server scenarios (start, login, a task to review, MCP door, `/state` p95) on `-Pengine=host`, `compose` or `kind`; JVM kill, container or pod replacement and an image upgrade on the old volume | docker, kind, kubectl | see `measurements.md` |
| `standTest` | live scenarios with a real agent | tokens | ~10–13 min |
| `promptEval`, `roleEval` | the brief and the Master judge their cases right | tokens | unknown |
| `roleEval -Prole=code-reviewer` | the reviewer's brief finds a case's file and pattern, and no finding on a clean diff; a rating becomes a case: `code-reviewer-lesson` | claude, tokens | unknown |

## A change runs the heavy suites it reaches

Before its commit, a change touching a path below runs that suite too; the session's commit gate reads this table and
adds them itself. 9509dbb2f changed the server starter with `test` alone and broke every login under compose.

| path (extended regex) | suite |
|---|---|
| `^(Dockerfile\|Dockerfile\.dist\|docker-compose\.yml\|entrypoint\.sh\|jagt\.server\.yml\.dist)$` | `standTest -Pmodelfree -Pengine=compose --rerun-tasks` |
| `^kubernetes\.yml\.dist$` | `standTest -Pmodelfree -Pengine=kind --rerun-tasks` |
| `^orchestrator-backend/docker/` | `standTest -Pmodelfree -Pengine=compose --rerun-tasks` |
| `^(board\|orchestrator-backend/src/boardTest)/` | `boardTest --rerun-tasks` |
| `^orchestrator-backend/stand/` | `standTest -Pmodelfree --rerun-tasks` |

## Context ceilings

- `test`, `boardTest` and `e2eTest` each end in a check summing Spring's cache misses from their test reports (`contexts-test`,
  `contexts-board-test`, `contexts-e2e-test` in `fitness.yml`): past the ceiling the build fails naming the suite and count.
- Ceilings start at the measured count and only go down ([0187](../decisions/0187-a-suite-creates-no-more-spring-contexts-than-its-ceiling.md)).

## Planned

