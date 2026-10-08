## MODIFIED Requirements

### Requirement: Door two takes a reported status
`flow/FlowReports` SHALL take a status a session reports over MCP, refused unless `FlowRules.refusedReport` allows
it. `REVIEWED` and `APPROVED` MUST land only through `FlowReports.read`, the host's round read. The
refusal MUST own the reason the agent acts on: no task talks itself onto a shared branch, out of one, closed, or
past its review.

#### Scenario: A forbidden report
- **WHEN** `FlowRules.refusedReport` refuses an agent's report
- **THEN** the agent gets the reason

#### Scenario: A session approves itself
- **WHEN** a session reports `APPROVED`
- **THEN** it is refused; only the round read lands it

### Requirement: Every deploy is checked where it landed
`DeployCheckJob` SHALL ask the session once per deploy commit and jagt run, keyed off what it asked, not the context
file other relays overwrite; how anything reaches it is the session's. Green MUST report nothing, the task staying
`DEPLOYED`; red is the session's `IN_PROGRESS`, the round running again: reviewed, deployed, checked.

#### Scenario: A deployed change fails its check
- **WHEN** the check fails
- **THEN** the task is `IN_PROGRESS`
