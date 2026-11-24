# 0239 — A bad practice fails the build at its first new use; an exception needs the owner and a reviewer

[← decisions](README.md) · rule: [`testing`](../rules/testing.md) · on [0162](0162-a-check-holds-for-all-code.md),
[0176](0176-every-kind-has-a-ceiling.md), [0180](0180-each-spheres-industry-standard-is-jagts-floor.md),
[0187](0187-a-suite-creates-no-more-spring-contexts-than-its-ceiling.md), [0237](0237-a-test-waits-on-state.md)

## Status

Accepted 2026-11-24, by the owner's order (JAGT-261).

## Context

- A rule only review enforces is met late; the build refuses a practice at its first new use.
- A practice with legacy uses is a counted row: a ratchet at today's count, lowered when the count falls, never raised.
- An exception granted in the commit that needs it is the author's own approval.

## Decision

- Each named bad practice is a `fitness.yml` row at `max 0`, or a ratchet at today's count:

| practice | row | count |
|----------|-----|-------|
| a test's timeout, sleep or thread of its own | `timing-in-tests`, `sleep-in-tests`, `wait-literal-in-tests` | 0 |
| a booted Spring context class in any test source set | `context-booting-tests` | 13 |
| `Thread.sleep` in main | `sleep-in-main` | 4 |

- A count above its number fails naming the row; a count below it fails asking to lower the number (`Report`).
- A row's exceptions carry `exception: "<number>"`, naming the decision that grants them.
- That decision's Status holds a line `Owner approved <date>` and a line `Reviewer agreed <date>, <who>`; so does any record
  whose Status opens a line with `Exception`. `DecisionIndexTest` fails one lacking either, and an `exception` naming no record.
- A decision in the same commit grants nothing without both lines: the reviewer is a person other than the author.

## Rejected

- Auto-editing `fitness.yml` when a count falls: the build never writes a tracked file; it fails and names the new number.
- The `contexts-*` suite rows as strict ratchets: a fork count varies (`e2eTest` 3 to 4, 0187).

## Reopen when

- A suite's context count is stable across forks: its `contexts-*` row becomes a ratchet.
