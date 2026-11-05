## ADDED Requirements

### Requirement: A read named under reads runs on OpenCode
A role named under `orchestrator.opencode.reads` SHALL have its read run as `opencode run` on the endpoint, on its model.
The QA checker read follows the role's declared tools, `master` names the round reads, and a role not named reads on `claude -p`.

#### Scenario: A named role
- **WHEN** `reads` names `qa` and ABC-42's QA check opens
- **THEN** the read runs on OpenCode on the model named for `qa`
- **AND** a fenced JSON answer is read as the check's verdict

#### Scenario: A role not named
- **WHEN** `reads` does not name `master`
- **THEN** the round reads run on `claude -p`

### Requirement: A read on OpenCode is held to its role's scope
A read SHALL be offered `read`, `grep`, `glob`, `bash` and, where the role fetches, `webfetch`, and no MCP server.
Its permissions SHALL deny every command first, allow the role's, deny its refused ones, and deny any redirect last.
Every completed tool call SHALL then be held to the same scope; one outside it discards the answer and answers failed.
Tokens SHALL be booked whatever the read answered, and a local failure SHALL NOT open the cloud's breaker.

#### Scenario: A redirect
- **WHEN** a read's `git log > f` ran
- **THEN** its answer is discarded and the check reports that it failed, naming the command

#### Scenario: A denied call
- **WHEN** a read's `touch b.txt` was denied by the permissions
- **THEN** its answer stands

#### Scenario: A fetch outside the environments
- **WHEN** a QA read fetched a URL under none of the run's environments
- **THEN** its answer is discarded
