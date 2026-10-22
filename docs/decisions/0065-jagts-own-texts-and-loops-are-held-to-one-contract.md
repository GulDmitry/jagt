# 0065 — jagt's own texts and loops are held to one contract

[← decisions](README.md) · rules: [`testing`](../rules/testing.md), [`components`](../rules/components.md#statejson)

## Status

Accepted 2026-10-22. Postmortem of the defects fixed on 2026-10-22.

## Context

| defect | introduced | why not caught | fixed |
|--------|------------|----------------|-------|
| review read refused `mr_discussions` | 7409c886 | read gate pinned per verb, no read tool row | f6992e9a |
| an identical round relayed into a fresh session | 459edae0 | dedup key in `task_context.md`, which every ship overwrote | dc8b74a6 |
| a round with nothing new shipped while a request was open | c7232cc2 | the spec said it, no test pinned it | eaaf53b0 |
| the brief forbade the deploy-conflict hand-off | 74312311 vs 0f42552a | brief and relayed texts tested apart | 624e172c |
| the Master answered a permission | unknown | an answer was assumed to grant whatever was asked | d5f3fe99 |
| an answer overwrote the pending hand-off | 0f42552a | every relay replaces the file, nothing marks one pending | 39d2a14f |
| a re-pressed conflict never handed again | 0f42552a | dedup key in memory, by text | 624e172c |
| a held line replaced by unrelated reports | unknown | no scenario for a report at a held status | d5f3fe99 |
| a plan the human asked for approved by the Master | unknown | no `plan`-mode row in the master matrix | 9cad8f04 |
| the log emptied at each start | unknown | nothing reads the run before | 9cad8f04 |
| palette verdict judged before the verbs arrived | unknown | the test typed once the page connected, not once it was read | d5b50025 |

A sub-agent also reported a cached Gradle green while the real run was red.

## Decision

| cause | what now checks it |
|-------|--------------------|
| A: a relayed text contradicts the brief | `SessionBriefContractTest`: worktrees and git operations relayed vs. granted |
| B: a prompt change reaches no model | rule in `testing.md`; `TextBudgetTest` names it on a prompt file |
| C: a loop the Master closes has no e2e | `MasterModeFlowTest` conflict hand-off, answer under it, permission; the rest listed in `testing.md` |
| D: a dedup key outlives or loses its writer | rule in `components.md`, the three left named; an answer goes under a pending instruction |
| E: an unattended wait has no end | rule in `components.md`, the two without one named |
| F: a cached green taken for a run | `--rerun-tasks` before a commit, `testing.md` and `AGENTS.md` |

## Rejected

- Scanning every string literal for git verbs: it reads code that is not relayed and misses what is formatted in.
- A model judging brief and instruction together: the contract is the machine's, as 0006 says.

## Reopen when

- A relayed instruction contradicts the brief again without `SessionBriefContractTest` failing.
