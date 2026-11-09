# acceptance Specification

## Purpose
The headless acceptance check of a deployed change against its ticket, run once per deploy where `orchestrator.roles.acceptance.enabled` is on.

## Requirements

### Requirement: A checker is configured under its role
`jagt.yml` SHALL say only whether a checker is on: `orchestrator.roles.<id>.enabled`, off unless true.
Its `model`, `environments`, `flows` and `workspace` SHALL be read from `roles/<id>/role.yml` over its `.dist`, again on every check.
jagt SHALL refuse to start while `jagt.yml` writes one of them, or `qa` or `roles.qa`, naming where it now lives.

#### Scenario: No key names acceptance
- **WHEN** `jagt.yml` does not write `roles.acceptance`
- **THEN** acceptance is off

#### Scenario: The old keys
- **WHEN** `jagt.yml` writes `roles: {acceptance: {environments: [...]}}`, `qa: {enabled: true}` or `roles: {qa: {enabled: true}}`
- **THEN** the start is refused, naming `roles/acceptance/role.yml` or `roles.acceptance` respectively

### Requirement: The start logs where each role value came from
At start jagt SHALL log, per installed role, one `role config` line for each of `enabled`, `model` and `environments`
(its count), naming the `origin`: `jagt.yml` or `default` for `enabled`, else `role.yml`, `role.yml.dist` or `default`.

#### Scenario: A model the human's copy names
- **WHEN** `roles/acceptance/role.yml` names `model: haiku`
- **THEN** the start logs `role config` with `role=acceptance key=model value=haiku origin=role.yml`

### Requirement: Acceptance checks a deployed change against its ticket
Where the role is enabled, one headless run SHALL check each criterion of the ticket on the environment, read-only, on the
folder's `model`. It answers one row per criterion (met, not met or unprovable), written to `acceptance-report.md`.
A `question` SHALL stand only behind an unprovable row. The run is told the deploy was pushed at a sha, never that it landed.

#### Scenario: Every row met
- **WHEN** ABC-42 is DEPLOYED and each target's pipeline is green
- **THEN** acceptance runs once on the tip's sha; all rows met marks it `accepted`

#### Scenario: A host that reports no deploy job
- **WHEN** acceptance runs on ABC-42 and the host reports no deploy job
- **THEN** the run is told the deploy was pushed at the sha, not that it is deployed, and proves landing itself

#### Scenario: A row proved
- **WHEN** every row is met or not met
- **THEN** the answer carries no question

### Requirement: An acceptance verdict is a label on the deploy's sha
Acceptance SHALL be a label, never a status: the card's status reads
`accepted` only while the passed verdict's sha is the deploy's tip. Every verdict SHALL carry the sha it checked. Spend is
metered as `acceptance check`.

#### Scenario: A later deploy
- **WHEN** ABC-42 is deployed again
- **THEN** the card no longer reads `accepted` until the new tip passes

### Requirement: A check opens only where it can run
A check SHALL open only for a task live on the deploy branch. Its pipelines MUST read green in `DeployCheckJob`, and the
folder's `environments` MUST name one. One check runs per deploy, kept as `acceptance-verdicts`, so a restart runs none twice.

#### Scenario: A restart after a check
- **WHEN** jagt restarts after acceptance ended ABC-42's check
- **THEN** the same deploy is not checked again

#### Scenario: No environment named
- **WHEN** `roles/acceptance/role.yml` and its `.dist` name no environment
- **THEN** no check runs

### Requirement: A check whose environment is down waits for it
A check that found its environment down SHALL hold with nobody until every environment answers its probe. The probe is its `landed` address where
one is named, else its own. A GET answered by anything but a server fault passes. Then the same check runs again.

#### Scenario: An environment that stays down
- **WHEN** acceptance found the environment down and its probe still fails
- **THEN** the check does not run again, however long it waits, and nobody is asked

#### Scenario: The environment answers
- **WHEN** every environment answers its probe
- **THEN** the same check runs again

### Requirement: What a check found goes where it is owed
A row not met SHALL go to the session as an `acceptance-round` (`Step.ACCEPTANCE_ROUND`, origin `acceptance`), the task `IN_PROGRESS`, counted as an
unattended move toward `loopBreaker`. A row unprovable, or a verdict with no row, is a question to you. A failed run stops the task for you
(`ACCEPTANCE_FAILED`) and its lift runs the check once more. A verdict on a sha that is no longer the deploy's tip is dropped.

#### Scenario: A row not met
- **WHEN** acceptance finds a row not met
- **THEN** the session receives the rows as an `acceptance-round`

#### Scenario: A verdict on a sha off the deploy branch
- **WHEN** the checked sha is no longer the tip when the verdict arrives
- **THEN** it is dropped and changes nothing on the card

### Requirement: A declined round holds DEPLOYED
A session answering an `acceptance-round` with `no_changes` SHALL leave the task DEPLOYED, since nothing on the deploy changed.
Its reply is the only door that lands the task there from IN_PROGRESS.

#### Scenario: The session disputes a row
- **WHEN** ABC-42 is IN_PROGRESS on an `acceptance-round` and its session reports `no_changes` with a reason
- **THEN** ABC-42 is DEPLOYED again and the reply stands as its report

### Requirement: A decline is read back to acceptance
A session's `no_changes` reply to an `acceptance-round` SHALL be read back to acceptance once the session has replied:
acceptance accepts it, which ends the check `declined`, or disputes it with the evidence against it. A first dispute SHALL be a question on
the DEPLOYED card. The Master in `act` answers it unless the human keeps answering, else the human does. A second dispute SHALL be a question
only the human answers. After it nothing is read for that deploy.

#### Scenario: Acceptance accepts the reason
- **WHEN** the session answers an `acceptance-round` with `no_changes` and acceptance finds no evidence against its reason
- **THEN** the check ends `declined` and the card keeps DEPLOYED with the reason in its hover

#### Scenario: Acceptance disputes once
- **WHEN** acceptance disputes the session's reason
- **THEN** the card asks the Master in `act`, else the human, and the session hears the answer

#### Scenario: Acceptance disputes again
- **WHEN** the session replies `no_changes` once more and acceptance disputes it again
- **THEN** the question waits for the human alone and acceptance reads nothing after it

### Requirement: Acceptance holds the turn while it reads
An acceptance run SHALL hold the task's turn from its start until it answers, so the board shows the agent at work.
No Master answers or deploys meanwhile. A stopped task is the human's. A check waiting on its environment holds nobody.
A turn left open and unanswered by a restart SHALL run the same check again on the same sha, and nothing is asked twice.

#### Scenario: A check reads
- **WHEN** acceptance reads ABC-42 on its deploy
- **THEN** acceptance holds the turn until the run answers, then nobody does

#### Scenario: A restart in the middle of a read
- **WHEN** jagt restarts with acceptance's turn on ABC-42 open and unanswered
- **THEN** the same check runs again on the same sha

### Requirement: A closed task refuses a late verdict
A task closed by you or by `ticket-close` SHALL end its check: a verdict arriving afterwards is refused and changes nothing.

#### Scenario: Closed while acceptance reads
- **WHEN** ABC-42 is closed while acceptance reads it and the verdict arrives
- **THEN** the verdict is refused and no round or question is raised

### Requirement: A checker declares its jobs and its read steps
A checker's `role.yml` SHALL declare `jobs:` (true = it runs jobs) and `readSteps:` (the brief steps whose turn opens its read).
The shipped acceptance declares `jobs: true` and `readSteps: [accept, again, decline]`. Two installed checkers naming one read step SHALL be refused.

#### Scenario: acceptance's read opens
- **WHEN** ABC-42 is DEPLOYED and the session is relayed to `accept`, `again` or `decline`
- **THEN** acceptance's read opens, as its `role.yml.dist` declares

#### Scenario: Two roles name one read step
- **WHEN** `acceptance` and a second checker both name `decline` in `readSteps`
- **THEN** jagt refuses the composition at startup, naming `decline`, `acceptance` and the second checker

### Requirement: A role names its tools as CLI-neutral globs
A role's `tools` SHALL name `shell` and `refuseShell`, the command globs a read may and may not run.
It SHALL name `refuseRead`, the path globs no read may open, beside `git` and `fetch`. Reads are always on and writes always refused.
A `role.yml` carrying `cli`, `allow` or `refuse` under `tools` SHALL be refused, naming the new keys.

#### Scenario: A moved key
- **WHEN** `roles/acceptance/role.yml` holds `tools: {allow: [Read]}`
- **THEN** the role is refused, naming `tools.shell`, `tools.refuseShell` and `tools.refuseRead`

### Requirement: A replayed acceptance read answers from its recording
With `orchestrator.agent.cli` set to `replay`, an acceptance read SHALL answer the recorded `ACCEPTANCE_CHECK` read: its
rows, its flow ids and its environment. It SHALL answer failed only where the recording holds no acceptance read.

#### Scenario: A recorded read names a flow
- **WHEN** the recording holds an acceptance read naming flow `@AC-1` and a listed environment
- **THEN** the replayed read answers those rows, flow and environment, and jagt runs the flow

#### Scenario: A recording without an acceptance read
- **WHEN** the recording holds no acceptance read
- **THEN** the replayed read answers failed with "the recording holds no acceptance check"

### Requirement: State kept while the role was called qa still loads
State written while the role was called `qa` SHALL load as the acceptance role's and be written back so. That covers the
`roles["qa"]` record, the top-level `qa`, the step `qa-round`, the origin `QA` and the stop trigger `QA_FAILED`.
The file `qa-verdicts` SHALL be read where `acceptance-verdicts` is absent.

#### Scenario: An old state file
- **WHEN** `state.json` holds `roles: {qa: {passed: "proj@abc"}}` and a stop `QA_FAILED`
- **THEN** the task loads with `acceptance` passed at that tip and the stop `ACCEPTANCE_FAILED`
