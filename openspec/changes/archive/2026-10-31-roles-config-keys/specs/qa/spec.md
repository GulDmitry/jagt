## ADDED Requirements

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

## MODIFIED Requirements

### Requirement: QA checks a deployed change against its ticket
With `orchestrator.roles.qa.enabled`, one headless QA run SHALL check each acceptance criterion of the ticket on the environment.
It is read-only and runs `orchestrator.roles.qa.model`, sonnet by default. It answers one row per criterion, each met,
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
