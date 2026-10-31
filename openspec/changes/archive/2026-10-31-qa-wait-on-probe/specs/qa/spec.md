## MODIFIED Requirements

### Requirement: A check opens only where it can run
A check SHALL open only for a task live on the deploy branch. Its pipelines MUST read green in `DeployCheckJob`, and
`orchestrator.qa.environments` MUST name an environment. One check runs per deploy, kept beside `state.json` as `qa-verdicts`,
so a restart runs none twice. A check that found its environment down holds with nobody until every environment answers its probe.
The probe is its `landed` address where one is named, else its own. A GET answered by anything but a server fault passes.
Then the same check runs again.

#### Scenario: A restart after a check
- **WHEN** jagt restarts after QA ended ABC-42's check
- **THEN** the same deploy is not checked again

#### Scenario: No environment named
- **WHEN** `orchestrator.qa.environments` is empty
- **THEN** no check runs

#### Scenario: An environment that stays down
- **WHEN** QA found the environment down and its probe still fails
- **THEN** the check does not run again, however long it waits, and nobody is asked

#### Scenario: The environment answers
- **WHEN** every environment answers its probe
- **THEN** the same check runs again
