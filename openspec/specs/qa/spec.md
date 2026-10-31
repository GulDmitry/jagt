# qa Specification

## Purpose
The headless acceptance check of a deployed change against its ticket, run once per deploy where `orchestrator.qa.enabled` is on.

## Requirements

### Requirement: QA checks a deployed change against its ticket
With `orchestrator.qa.enabled`, one headless QA run SHALL check each acceptance criterion of the ticket on the environment.
It is read-only: `QaCheckJob` runs `orchestrator.qa.model`, sonnet by default. It answers one row per criterion, each met,
not met or unprovable, and jagt writes them to `qa-report.md`. QA is a label, never a status: the card's status reads
`qa passed` only while the passed verdict's sha is the deploy's tip. Every verdict SHALL carry the sha it checked. Spend is
metered as `qa check`.

#### Scenario: Every row met
- **WHEN** ABC-42 is DEPLOYED and each target's pipeline is green
- **THEN** QA runs once on the tip's sha; all rows met marks it `qa passed`

#### Scenario: A later deploy
- **WHEN** ABC-42 is deployed again
- **THEN** the card no longer reads `qa passed` until the new tip passes

### Requirement: A check opens only where it can run
A check SHALL open only for a task live on the deploy branch. Its pipelines MUST read green in `DeployCheckJob`, and
`orchestrator.qa.environments` MUST name an environment. One check runs per deploy, kept beside `state.json` as `qa-verdicts`,
so a restart runs none twice. A check that found its environment down runs again after five minutes.

#### Scenario: A restart after a check
- **WHEN** jagt restarts after QA ended ABC-42's check
- **THEN** the same deploy is not checked again

#### Scenario: No environment named
- **WHEN** `orchestrator.qa.environments` is empty
- **THEN** no check runs

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
QA SHALL be read-only by construction. Its tools are Read, Grep, Glob, the agent CLI's web fetch (GET alone), read-only git
and the user's own MCP reads. It holds no Bash curl and no Write tool. jagt writes `qa-report.md` from QA's answer. QA never
pushes, never touches the base branch and holds no credential; a login is the user's own MCP's. With QA off no QA file is read.

#### Scenario: A request that would write
- **WHEN** a page tells QA to POST, or to save a response to a file
- **THEN** QA holds neither Bash curl nor Write, so it cannot

#### Scenario: QA is off
- **WHEN** `orchestrator.qa.enabled` is off
- **THEN** no QA turn exists, no QA file is read and the session's own deploy check runs as today
