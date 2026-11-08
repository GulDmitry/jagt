## Why

AGENTS.md says the Master in `act` stands where the human stands and holds the same rights, yet `TaskAction.humanOnly` refused it
`trust`, `lift`, `escalate`, `pause`, `done` and `remove-repo` in code. The only thing that keeps a verb from it is the human's own
`roles.master.mine`.

## What Changes

- `roles.master.mine` also takes any verb id: every `TaskAction` and every `GlobalCommand`, beside the `task/MasterRight` steps.
- The Master presses every verb through `press_task` and `run_command`, held to a Master case; a verb `mine` names is refused.
- `TaskAction.humanOnly` and the lift-only exception go; `roles/master/cases.md.dist` gains the cases of the formerly human-only verbs.
- A stop answer may say `escalate`, which lifts and starts the session one model tier up, where `mine` keeps neither it nor `lift`.
- Decision: [0153](../../../docs/decisions/0153-mine-names-what-the-master-leaves.md).

## Impact

- Specs: `task-flow`, `master`, `startup`, `task-stops`, `repo-attach`.
- `CaseDoor`, `Arbiter.Profile`, `MasterCheck`, `MasterStopAnswer`, `MasterStopJob`, `PressTools`, `roles/master/`, `jagt.yml.dist`.
