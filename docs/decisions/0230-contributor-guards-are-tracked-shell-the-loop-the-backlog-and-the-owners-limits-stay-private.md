# 0230 — Contributor guards are tracked shell; the loop, the backlog and the owner's limits stay private

[← decisions](README.md) · rule: [`AGENTS.md`](../../AGENTS.md) "First priority" · kind: [`ARCHITECTURE.md`](../../ARCHITECTURE.md#the-kinds) ·
on [0162](0162-a-check-holds-for-all-code.md), [0180](0180-each-spheres-industry-standard-is-jagts-floor.md),
[0227](0227-a-script-with-logic-is-java.md)

## Status

Accepted 2026-11-23.

## Context

- Four guards stopped a contributor's agent session from wasting or breaking a shared machine: a polling loop,
  a kill of every Gradle daemon, a write by a read-only reviewer, a push whose tree no local CI had run.
  They lived in one owner's git-ignored tree, so no other contributor had them.
- A hook runs on every tool call. Measured 2026-11-22 on a laptop: `jq` answers in 0.02 s, a JVM in 0.9 s per call.
- 0227 sends logic to Java and lets shell stay where Java's start breaks the latency budget; a per-call hook is that case.
- Three parts are one person's setup, not the project's: the loop that drives sessions, the backlog tooling and the budget limits.

## Decision

- **A contributor guard is a shell script in `scripts/guards/`**: the bare command as text on stdin, exit 2 blocks
  with its reason on stderr, any other outcome exits 0 (fail-open, [`guards`](../rules/guards.md)).
- **The tracked `.claude/settings.json` pipes `jq -r .tool_input.command` into each guard**; a guard knows no payload format.
- **A guard names no vendor, session or user**: a pid variable to protect is an argument,
  the agent a guard applies to is the settings entry's `jq` filter.
- **`orchestrator-backend/scripts/ci-local.sh` records a tree's hash** after a green `linux-suite.sh`;
  `push-after-local-ci.sh` lets a push from `release` or a hotfix branch through only on that tree.
- **`guardsTest`** runs shellcheck and one bats case per promise, in the Linux suite.
- **Stay private**: the loop, the backlog guards and hooks, the cost digest and the owner's limits.

## Rejected

- Java guards: 0.9 s on every tool call.
- A guard reading the CLI's JSON payload: a second contract in every script.
- Shipping the owner's whole harness: it names a loop, a backlog and limits no other contributor has.

## Reopen when

- A guard passes 100 lines or gains state across calls.
- A JVM answers under 0.1 s per call.
