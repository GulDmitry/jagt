## MODIFIED Requirements

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
