## MODIFIED Requirements

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

### Requirement: QA writes nothing but its report
QA SHALL be read-only by construction: its tools are Read, Grep, Glob, the agent CLI's web fetch (GET alone), read-only git
and the user's own MCP reads, with no Bash curl and no Write tool. jagt writes `qa-report.md` from QA's answer. QA never
pushes, never touches the base branch and holds no credential; a login is the user's own MCP's. With QA off no QA file is read.

#### Scenario: A request that would write
- **WHEN** a page tells QA to POST, or to save a response to a file
- **THEN** QA holds neither Bash curl nor Write, so it cannot

#### Scenario: QA is off
- **WHEN** `orchestrator.qa.enabled` is off
- **THEN** no QA turn exists, no QA file is read and the session's own deploy check runs as today
