# 0201 — An approved request deploys itself when `deploy.onApproval` is on

[← decisions](README.md) · on [0133](0133-a-red-deploy-pipeline-is-rerun-once.md),
[0198](0198-the-acceptance-workspace-is-proposed-by-a-request.md)

## Status

Accepted 2026-11-19, owner's choice (JAGT-133).

## Context

- On a server a request may wait weeks for an approval; nobody sits at the board to press `deploy` once it comes.
- The Master deploys in `act`, but it is an optional role: a server without it left an approved request standing.
- AGENTS.md allowed no unasked deploy but the Master's.

## Decision

- A new unasked actor: the approval of a request, read from the code host.
- `deploy.onApproval` in `jagt.yml` turns it on; the default is `false`, the server starter sets `true`.
- `DeployOnApprovalJob` watches `APPROVED` and presses the one `deploy` a human's press runs, through `CommandService`;
  every guard of that deploy holds, and a deploy conflict stops the task for a human as ever.
- The history names `ActionOrigin.CODE_HOST` as who acted; no role, not even the Master, is needed.
- One press per arrival at `APPROVED`: a task a human or the Master already deployed is no longer `APPROVED` and is left alone.
- A refused press logs one WARN and is not pressed again for that arrival.
- The Master's own deploy in `act` is unchanged.
- `autoReview.waitingMaxIntervalMinutes` defaults to 60: a request waiting for an approval is read hourly.

## Rejected

- The press stamped `MASTER` or `JAGT`: the approval is the cause, and the Master's brake counts its own moves.
- A hook in the auto-review round: it runs only with `autoReview` on, and the deploy must not hang on it.
- On by default on a desktop: a person there presses `deploy` while watching.

## Reopens when

- A second event than an approval is asked to deploy.
- A host reports an approval jagt cannot tell from a withdrawn one.
