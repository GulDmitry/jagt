# 0156 — A poll reads an unchanged request from the host alone

[← decisions](README.md) · rule: [`review`](../../openspec/specs/review/spec.md)

## Status

Accepted 2026-11-09. Beside [0130](0130-every-third-party-is-a-configured-seam.md) and
[0148](0148-jagt-reaches-the-code-host-through-its-cli-login.md).

## Context

- Every auto-review poll paid a model read of each open request, approved and deployed ones included; most found it unchanged.
- `AutoReviewCadence.polls` keeps those requests on purpose: a reviewer writes on an open request whatever the task does.

## Decision

- `CodeHost.requestFingerprint` is the host's cheap read of a request: state, update time, head commit, checks, reviews.
- `github`: one `gh pr view --json … --jq`. `gitlab`: the merge request and its approvals. `command`, `none`, `replay`: none.
- `ReviewRound` reads the fingerprint and the unresolved note ids before the paid read, and keeps that read beside them.
- An auto-review poll finding both equal keeps the last verdict and pays nothing; anything else reads as before.
- A sweep from any other origin always reads: a human's sweep is how a wrong model read is corrected.
- The last read lives in memory, 64 requests at most: no round's facts are persisted, so a restart reads once.

## Rejected

- Stopping polls of approved or deployed tasks: a late review comment would never reach the task.
- The update time alone: neither host promises that a finished check or a resolved thread moves it.

## Reopen when

- A host's fingerprint is found equal over a change a round reads.
