# 0153 — Mine names what the Master leaves

[← decisions](README.md) · rule: [`master`](../../openspec/specs/master/spec.md), [`task-flow`](../../openspec/specs/task-flow/spec.md)

## Status

Accepted 2026-11-08 by the owner's order. Supersedes in part [0016](0016-only-the-human-closes-a-task.md) and
[0152](0152-a-stop-lifts-on-the-next-model-tier.md): what they keep human is now the human's to keep in `mine`.

## Context

AGENTS.md: the Master in `act` stands where the human stands and holds the same rights. `TaskAction.humanOnly` refused it
`trust`, `lift`, `escalate`, `pause`, `done` and `remove-repo` in code, a second list beside `roles.master.mine`.

## Decision

- `roles.master.mine` is the one list of what stays the human's; it names steps (`MasterRight`), any `TaskAction` id and any `GlobalCommand` id.
- `CaseDoor` refuses the Master a verb `mine` names (`ACTION_NOT_AVAILABLE`), else holds it to a Master case.
- `plan` keeps `approve-plan`, `repo` keeps `add-repo`: one word, one thing held.
- The Master's own path to every verb is `MasterPresses` (`press_task`, `run_command`) through the door a human's press uses.
- Every `TaskAction` has a `MasterMove` and a shipped Master case (`MasterVerbsTest`): a new verb without one fails the build.
- A stop answer may say `escalate`, spending the Master's one lift between the human's, unless `mine` keeps `escalate` or `lift`.
- `do`, `resume` and `act` stay closed whatever `mine` says: a task opens from a deterministic source, a brake on the Master is not its to lift.
- Unasked, the Master still closes nothing: `MasterShip` leaves a round with nothing to ship to the human.

## Rejected

- A new key beside `mine`: two lists for one question.
- Keeping `done` human by a table: a rule the human cannot change is a rule against the Master's rights.

## Reopen when

- A Master press of a formerly human-only verb is later undone by the human more often than not.
