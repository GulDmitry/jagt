# Test suites

[← testing](testing.md)

| group | suites | where | when |
|---|---|---|---|
| **Unit + functional** | `./gradlew test`: each class, AGENTS.md's rules (ArchUnit, budgets), jqwik properties, mutants on the changed lines, 0.80 line coverage per module | locally and CI | every commit; CI on a push or pull request |
| **Heavy** | `boardTest`, `e2eTest`, `linuxDriverTest`, `containerTest` | CI | nightly and on a release tag |
| **Stand** | `standTest -Pmodelfree`, a live `standTest`, `promptEval`, `roleEval -Prole=master` | locally only: docker, an agent login, tokens | before a minor release |

CI sees only what is pushed, and jagt pushes at a release: a heavy break in new code shows on the release push, which
waits for it. Times are wall clock, dated in `measurements.md`.

## What each suite proves

| suite | proves | needs | time |
|---|---|---|---|
| `test` | units, rules, properties, mutants on the diff | JDK | ~2 min |
| `boardTest` | the board in Chromium: cards, verbs, tabs, reports | Playwright | ~1.5 min |
| `e2eTest` | a task's flow through real git, tmux and a scripted agent | git, tmux | ~2 min |
| `linuxDriverTest` | the Linux terminal and desktop drivers | a Linux display | unknown |
| `containerTest` | jagt's image under compose: start, readiness, login over HTTP | docker | ~2.5 min |
| `standTest -Pmodelfree` | recorded task flows replayed in jagt's container, a restart mid-turn included | docker | ~2 min |
| `standTest` | live scenarios with a real agent | tokens | ~10–13 min |
| `promptEval`, `roleEval` | the brief and the Master judge their cases right | tokens | unknown |

## A change runs the heavy suites it reaches

Before its commit, a change touching a path below runs that suite too; the session's commit gate reads this table and
adds them itself. 9509dbb2f changed the server starter with `test` alone and broke every login under compose.

| path (extended regex) | suite |
|---|---|
| `^(Dockerfile\|Dockerfile\.dist\|docker-compose\.yml\|entrypoint\.sh\|jagt\.server\.yml\.dist\|kubernetes\.yml\.dist)$` | `containerTest --rerun-tasks` |
| `^orchestrator-backend/(docker\|src/containerTest)/` | `containerTest --rerun-tasks` |
| `^(board\|orchestrator-backend/src/boardTest)/` | `boardTest --rerun-tasks` |
| `^orchestrator-backend/stand/` | `standTest -Pmodelfree --rerun-tasks` |

## Context ceilings

- `test`, `boardTest` and `e2eTest` each end in a check summing Spring's cache misses from their test reports (`contextsTest`,
  `contextsBoardTest`, `contextsE2eTest` in `complexity-ceilings.json`): past the ceiling the build fails naming the suite and count.
- Ceilings start at the measured count and only go down ([0186](../decisions/0187-a-suite-creates-no-more-spring-contexts-than-its-ceiling.md)).

## Planned

- JAGT-102: kind as a second stand engine, `standTest -Pmodelfree -Pengine=kind`.
