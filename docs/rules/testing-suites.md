# Test suites

[← testing](testing.md)

Every test jagt has, its kind, and where and when it runs. CI is [`.github/workflows/ci.yml`](../../.github/workflows/ci.yml):
every job runs per push to `main`, per pull request, per `v*` tag and **nightly at 04:00 UTC**; a tag's release waits for all of them.
Times are wall clock, measured and dated in `measurements.md`.

| suite | kind | proves | needs | locally | CI job | time |
|---|---|---|---|---|---|---|
| `test` | unit, rule, property, mutation | each class, AGENTS.md's rules (ArchUnit, budgets), jqwik properties, mutants on changed lines | JDK | every commit | `unit` | ~2 min |
| `e2eTest` | end to end | a task's flow through real git, tmux and a scripted agent | git, tmux | where a change reaches it; before a release | `linux` | ~2 min |
| `linuxDriverTest` | integration | the Linux terminal and desktop drivers | a Linux display | — | `linux` | unknown |
| `boardTest` | UI end to end | the board in Chromium: cards, verbs, tabs, reports | Playwright | any change to `board/` | `board` | ~1.5 min |
| `npm run lint` in `board/` | static | Prettier format, ESLint rules over the board's modules | Node | any change to `board/` | none yet | seconds |
| `containerTest` | image integration | jagt's image under compose: start, readiness, login over HTTP | docker | any change to the image | `image` | ~2.5 min |
| `scripts/container-suite.sh` | image end to end | builds the image, logs in, `BoardContainerTest` against it, then `containerTest` | docker | a change to `Dockerfile`, `entrypoint.sh` or `jagt.server.yml.dist` | `image` | unknown |
| `standTest -Pmodelfree` | system, replayed | recorded task flows in jagt's container, a restart mid-turn included | docker | before a release | none yet | ~2 min |
| `standTest -Pmodelfree -Plocal` | system, replayed | the same flows on the host process | git, tmux | without docker | — | ~2.5 min |
| `standTest` | system, live | live scenarios with a real agent | an agent login, tokens | release only | — | ~10–13 min |
| `promptEval` | eval, spends tokens | the session brief routes as its cases say | an agent login | a change to `roles/session/brief.md.dist` | — | unknown |
| `roleEval -Prole=<id>` | eval, spends tokens | a role (master, acceptance, triage) judges its cases right | an agent login | a change to that role; before a release | — | unknown |
| `pitest` | mutation, whole module | every mutant of a module, beyond the diff `test` covers | JDK | by name | — | minutes per module |

Only `test` gates a commit; the rest run as the "locally" column says (AGENTS.md "Tests"). Nothing that spends tokens runs in CI.

## Planned

- JAGT-102: kind becomes a third stand engine, `standTest -Pmodelfree -Pengine=compose|kind`, compose in `image`, kind nightly.
- JAGT-96: the board's lint and its unit tests get a CI job of their own.
- JAGT-105: `boardTest` opens the Code tab in Firefox and WebKit too.
