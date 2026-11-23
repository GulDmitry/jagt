# Test suites

[← testing](testing.md)

| group | suites | where | when |
|---|---|---|---|
| **Unit + functional** | `./gradlew test`: each class, AGENTS.md's rules (ArchUnit, budgets), jqwik properties (50 tries, 200 at the release gate by `-PjqwikTries`), mutants on the changed lines, 0.80 line coverage per module | locally and CI | every commit; CI on a push or pull request |
| **Heavy** | `boardTest`, `e2eTest`, `linuxDriverTest` | CI | the `nightly` workflow (03:17 UTC schedule, calls `ci.yml`), a pull or merge request |
| **Stand** | `standTest -Pmodelfree -Pengine=host\|compose\|kind`, a live `standTest`, `promptEval`, `roleEval -Prole=master` | locally: docker, an agent login, tokens; CI runs `standTest -Pmodelfree` in its `stand` job | before a minor release; CI per push (compose), nightly (kind) |

CI's `stand` job runs `standTest -Pmodelfree`; absolute time budgets need `-Pbudgets=true` on the idle reference machine,
per `strategy.matrix.engine`: compose per push and pull request, kind nightly and per pull request;
GitLab runs compose alone, since kind needs a privileged runner.
CI sees only what is pushed: a heavy break in new code shows on the pull request, which waits for it.
A `schedule` run starts late under GitHub load, worst at the top of the hour,
and runs the default branch's latest commit
([events](https://docs.github.com/en/actions/reference/workflows-and-actions/events-that-trigger-workflows),
checked 2026-11-19): the old `0 4` cron started 08:20-08:40 late on 2026-11-16..18, so the cron sits off the hour
and `nightly` logs its delay. Under heavy load GitHub drops a scheduled run (none on 2026-11-23):
`gh workflow run nightly.yml --ref main` starts it on demand. Times are wall clock, dated in `measurements.md`.

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

