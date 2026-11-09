## MODIFIED Requirements

### Requirement: A role may read on OpenCode
`orchestrator.opencode.reads` SHALL map a role id (or `master`, the round reads) to the model its read runs on, a blank
value meaning `orchestrator.opencode.model`. A role not named reads as before. Startup SHALL refuse a key that is neither
`master` nor an installed role, `reads` without `orchestrator.opencode.endpoint`, and a role resolving to no model.
It SHALL also refuse the binary missing from PATH wherever a read or the sessions run on OpenCode.

#### Scenario: An unknown role
- **WHEN** `reads` names `qaa` and no installed role has that id
- **THEN** the start is refused, naming `orchestrator.opencode.reads`

#### Scenario: Reads without an endpoint
- **WHEN** `reads` names `acceptance` and `endpoint` is blank
- **THEN** the start is refused, naming `orchestrator.opencode.endpoint`, so no read falls to the user's own provider

#### Scenario: A role with no model
- **WHEN** `reads` names `master` with a blank value and `model` is blank
- **THEN** the start is refused, naming `orchestrator.opencode.reads`

#### Scenario: A role reads on the endpoint
- **WHEN** `reads` is `{acceptance: qwen-4b}` with an endpoint set
- **THEN** the start is accepted while the sessions stay on the default agent CLI

### Requirement: A read named under reads runs on OpenCode
A role named under `orchestrator.opencode.reads` SHALL have its read run as `opencode run` on the endpoint, on its model.
The acceptance checker read follows the role's declared tools, `master` names the round reads, and a role not named reads on `claude -p`.

#### Scenario: A named role
- **WHEN** `reads` names `acceptance` and ABC-42's acceptance check opens
- **THEN** the read runs on OpenCode on the model named for `acceptance`
- **AND** a fenced JSON answer is read as the check's verdict

#### Scenario: A role not named
- **WHEN** `reads` does not name `master`
- **THEN** the round reads run on `claude -p`
