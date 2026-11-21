# 0221 — What a press reads is three ports of the chart

[← decisions](README.md) · rule: [`AGENTS.md`](../../AGENTS.md) · relates: [0040](0040-git-runs-only-what-the-human-trusted.md),
[0102](0102-a-request-is-merged-when-its-tip-reached-the-target-in-git.md), [0080](0080-a-repeating-round-ends-in-one-stop.md)

## Status

Accepted 2026-11-21. Reopens on the condition below.

## Context

- `ship`, `deploy` and `revert` were refused by three `CapabilityInterceptor`s in `usecase/`, after the chart offered them.
- `TaskOffers` was therefore not the only legal-move set: a verb could be offered and then refused.
- `core` cannot import `usecase`, so the chart could not call those reads.
- The `/state` budget (p95 100 ms over 50 calls, `stand-engines`) forbids a git read or a merged refresh in the projection.

## Decision

- `core/port` holds three seams: `GitDistrust` (git against what was trusted), `MergedRefresh` and `CurrentOrigin`.
- `usecase` implements them; the `ports` ceiling of [0176](0176-every-kind-has-a-ceiling.md) rises 53 to 56.
- `FactSources` hands the first two to `Facts` as suppliers read once, on a press of a verb they gate only.
- The projection passes suppliers that read nothing.
- The origin joins `ChartFacts`; a projection counts as human.
- `TaskOffers` offers the three verbs only where those reads say go.
- `Decline` carries the sentence a press is refused with: the human and the Master read the same words.
- A merged read answers `Outcome.nothing`, not a refusal; an unreadable git state throws from the source.
- `CapabilityInterceptor` stays for what runs after the work (`DeployLiftsStop`).

## Rejected

- Keeping the interceptors: a second legal-move set beside the chart.
- Reading git in the projection: breaks the `/state` budget on every board render.

## Reopen when

- A fourth press-time read joins these three, or the chart gains a cheaper way to carry a read that costs a process.
