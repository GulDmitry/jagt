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

## Planned

- JAGT-102: kind as a second stand engine, `standTest -Pmodelfree -Pengine=kind`.
- JAGT-96: the board's own unit tests and lint join unit + functional.
