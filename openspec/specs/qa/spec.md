# qa Specification

## Purpose
The headless acceptance check of a deployed change against its ticket, run once per deploy where `orchestrator.roles.qa.enabled` is on.

## Requirements

### Requirement: A checker is configured under its role
A checker SHALL be configured under `orchestrator.roles.<id>`, with `enabled`, `model` and `environments`. It is off unless
`enabled` is true. The old `orchestrator.qa` key SHALL still be read for the role `qa`; where `roles.qa` is written, it wins.
Reading the old key logs one deprecation line and is never a startup problem.

#### Scenario: Neither key names QA
- **WHEN** `jagt.yml` writes neither `roles.qa` nor `qa`
- **THEN** QA is off

#### Scenario: Only the old key
- **WHEN** `jagt.yml` writes `qa: {enabled: true}` and no `roles.qa`
- **THEN** QA is on, and one deprecation line is logged

#### Scenario: Both keys
- **WHEN** `jagt.yml` writes `qa: {enabled: true}` and `roles: {qa: {enabled: false}}`
- **THEN** QA is off

### Requirement: QA checks a deployed change against its ticket
With `orchestrator.roles.qa.enabled`, one headless QA run SHALL check each acceptance criterion of the ticket on the environment.
It is read-only and runs `orchestrator.roles.qa.model`, sonnet by default. It answers one row per criterion, each met,
not met or unprovable, and jagt writes them to `qa-report.md`. A `question` SHALL stand only behind an unprovable row.
The run is told the deploy was pushed at a sha, never that it landed: landing is QA's to prove.
QA is a label, never a status: the card's status reads
`qa passed` only while the passed verdict's sha is the deploy's tip. Every verdict SHALL carry the sha it checked. Spend is
metered as `qa check`.

#### Scenario: Every row met
- **WHEN** ABC-42 is DEPLOYED and each target's pipeline is green
- **THEN** QA runs once on the tip's sha; all rows met marks it `qa passed`

#### Scenario: A later deploy
- **WHEN** ABC-42 is deployed again
- **THEN** the card no longer reads `qa passed` until the new tip passes

#### Scenario: A host that reports no deploy job
- **WHEN** QA runs on ABC-42 and the host reports no deploy job
- **THEN** the run is told the deploy was pushed at the sha, not that it is deployed, and proves landing itself

#### Scenario: A row proved
- **WHEN** every row is met or not met
- **THEN** the answer carries no question

### Requirement: A check opens only where it can run
A check SHALL open only for a task live on the deploy branch. Its pipelines MUST read green in `DeployCheckJob`, and
`orchestrator.roles.qa.environments` MUST name an environment. One check runs per deploy, kept beside `state.json` as `qa-verdicts`,
so a restart runs none twice. A check that found its environment down holds with nobody until every environment answers its probe.
The probe is its `landed` address where one is named, else its own. A GET answered by anything but a server fault passes.
Then the same check runs again.

#### Scenario: A restart after a check
- **WHEN** jagt restarts after QA ended ABC-42's check
- **THEN** the same deploy is not checked again

#### Scenario: No environment named
- **WHEN** `orchestrator.roles.qa.environments` is empty
- **THEN** no check runs

#### Scenario: An environment that stays down
- **WHEN** QA found the environment down and its probe still fails
- **THEN** the check does not run again, however long it waits, and nobody is asked

#### Scenario: The environment answers
- **WHEN** every environment answers its probe
- **THEN** the same check runs again

### Requirement: What a check found goes where it is owed
A row not met SHALL go to the session as a `qa-round` (`Step.QA_ROUND`, origin `qa`), the task `IN_PROGRESS`, counted as an unattended
move toward `loopBreaker`. A row unprovable, or a verdict with no row, is a question to you. A failed run stops the task for
you (`QA_FAILED`) and its lift runs the check once more. A verdict on a sha that is no longer the deploy's tip is dropped.

#### Scenario: A row not met
- **WHEN** QA finds a row not met
- **THEN** the session receives the rows as a `qa-round`

#### Scenario: A verdict on a sha off the deploy branch
- **WHEN** the checked sha is no longer the tip when the verdict arrives
- **THEN** it is dropped and changes nothing on the card

### Requirement: QA writes nothing but its report
QA SHALL be read-only by construction. Its tools are Read, Grep, Glob, the agent CLI's web fetch, `curl -sS [-i] <url>`,
read-only git and the user's own MCP reads. Its curl is a GET of a `roles.qa.environments` URL alone. It holds no Write tool. jagt writes
`qa-report.md` from QA's answer. QA never pushes, never touches the base branch and holds no credential; a login is the user's
own MCP's. With QA off no QA file is read.

#### Scenario: A request that would write
- **WHEN** a page tells QA to POST, or to save a response to a file
- **THEN** the read gate refuses any curl but `curl -sS [-i] <url>` and QA holds no Write, so it cannot

#### Scenario: An environment the web fetch cannot reach
- **WHEN** `roles.qa.environments` names `http://127.0.0.1:5000`, which the web fetch refuses as a host without a dot
- **THEN** QA reads it with `curl -sS http://127.0.0.1:5000/version`, and a curl of any other host is refused

#### Scenario: QA is off
- **WHEN** `orchestrator.roles.qa.enabled` is off
- **THEN** no QA turn exists, no QA file is read and the session's own deploy check runs as today

### Requirement: A declined round holds DEPLOYED
A session answering a `qa-round` with `no_changes` SHALL leave the task DEPLOYED, since nothing on the deploy changed.
Its reply is the only door that lands the task there from IN_PROGRESS.

#### Scenario: The session disputes a row
- **WHEN** ABC-42 is IN_PROGRESS on a `qa-round` and its session reports `no_changes` with a reason
- **THEN** ABC-42 is DEPLOYED again and the reply stands as its report

### Requirement: A decline is read back to QA
A session's `no_changes` reply to a `qa-round` SHALL be read back to QA once the session has replied: QA accepts it, which ends
the check `declined`, or disputes it with the evidence against it. A first dispute SHALL be a question on the DEPLOYED card.
The Master in `act` answers it unless the human keeps answering, else the human does. A second dispute SHALL be a question
only the human answers. After it nothing is read for that deploy.

#### Scenario: QA accepts the reason
- **WHEN** the session answers a `qa-round` with `no_changes` and QA finds no evidence against its reason
- **THEN** the check ends `declined` and the card keeps DEPLOYED with the reason in its hover

#### Scenario: QA disputes once
- **WHEN** QA disputes the session's reason
- **THEN** the card asks the Master in `act`, else the human, and the session hears the answer

#### Scenario: QA disputes again
- **WHEN** the session replies `no_changes` once more and QA disputes it again
- **THEN** the question waits for the human alone and QA reads nothing after it

### Requirement: QA holds the turn while it reads
A QA run SHALL hold the task's turn from its start until it answers, so the board shows the agent at work.
No Master answers or deploys meanwhile. A stopped task is the human's. A check waiting on its environment holds nobody.
A turn left open and unanswered by a restart SHALL run the same check again on the same sha, and nothing is asked twice.

#### Scenario: A check reads
- **WHEN** QA reads ABC-42 on its deploy
- **THEN** QA holds the turn until the run answers, then nobody does

#### Scenario: A restart in the middle of a read
- **WHEN** jagt restarts with QA's turn on ABC-42 open and unanswered
- **THEN** the same check runs again on the same sha

### Requirement: A closed task refuses a late verdict
A task closed by you or by `ticket-close` SHALL end its check: a verdict arriving afterwards is refused and changes nothing.

#### Scenario: Closed while QA reads
- **WHEN** ABC-42 is closed while QA reads it and the verdict arrives
- **THEN** the verdict is refused and no round or question is raised
