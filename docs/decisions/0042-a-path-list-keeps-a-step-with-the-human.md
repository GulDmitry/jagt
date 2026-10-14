# 0042 — A path list keeps a step with the human

[← decisions](README.md) · rule: [`master`](../../openspec/specs/master/spec.md)

## Status

Accepted 2026-10-14.

## Context

- In `act` the Master ships and deploys whatever passed its read. OpenJCockpit's `agents-rules.md` stops for a human on
  authentication, authorization, logging, retention, data exports and secrets; the owner asked for the same.
- The roadmap forbids a matrix of per-point toggles: a configuration able to leave a step ownerless is a bug nobody
  finds.

## Decision

- A project's `humanPaths` lists globs. In `act`, a task changing a path they name is shipped and deployed by the human
  alone, as `mine` keeps a step (`HumanPaths`, `MasterPress`); the `master` window names the path.
- Paths git cannot read count as matched: a failed read is never "not found" (0008).
- It is not a per-point toggle: it names no step and no owner, only facts about one diff. It can hand a step to the
  human and never take one away, so no setting leaves a step ownerless; the fallback stays the human (0006).
- Ship and deploy only: `revert` is already the human's, and the round is still read and its findings sent back.

## Rejected

- `master.humanPaths`, one list for every project: a glob names a path inside one repository.
- A per-step list of paths: a matrix by another name, and a deploy kept while its ship is pressed reaches no one.
- Letting the Master's read decide what is risky: the trigger is the machine's, the judgement alone a model's (0006).

## Reopen when

- A project needs a step other than ship and deploy kept by path.
- The human deploys, unchanged, most tasks `humanPaths` held: the list names more than it should.
