## MODIFIED Requirements

### Requirement: A checker is configured under its role
`jagt.yml` SHALL say only whether a checker is on: `orchestrator.roles.<id>.enabled`, off unless true.
Its `model`, `environments` and `flows` SHALL be read from `roles/<id>/role.yml` over its `.dist`, again on every check.
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

### Requirement: A check opens only where it can run
A check SHALL open only for a task live on the deploy branch. Its pipelines MUST read green in `DeployCheckJob`, and the
folder's `environments` MUST name one. One check runs per deploy, kept as `acceptance-verdicts`, so a restart runs none twice.

#### Scenario: A restart after a check
- **WHEN** jagt restarts after acceptance ended ABC-42's check
- **THEN** the same deploy is not checked again

#### Scenario: No environment named
- **WHEN** `roles/acceptance/role.yml` and its `.dist` name no environment
- **THEN** no check runs

### Requirement: Acceptance writes nothing but its report
Acceptance SHALL be read-only by construction. Its tools are Read, Grep, Glob, the agent CLI's web fetch, `curl -sS [-i] <url>` and read-only git,
beside the read-shaped tools of `assistant.allowedTools` and its `tools.mcp`. A browser server there also navigates and looks, writing no file. Its
curl and its navigation reach a listed environment alone. It holds no Write tool. jagt writes `acceptance-report.md` from acceptance's answer.
Acceptance never pushes, never touches the base branch and holds no credential; a login is the user's own MCP's. Off, it reads no acceptance file.

#### Scenario: A request that would write
- **WHEN** a page tells acceptance to POST, to save a response to a file, or to click or type in its browser
- **THEN** the read gate refuses any curl but `curl -sS [-i] <url>` and any MCP write verb, and acceptance holds no Write

#### Scenario: An environment the web fetch cannot reach
- **WHEN** the folder's `environments` names `http://127.0.0.1:5000`, which the web fetch refuses as a host without a dot
- **THEN** acceptance reads it with `curl -sS http://127.0.0.1:5000/version`, and a curl of any other host is refused

#### Scenario: A browser navigation
- **WHEN** acceptance navigates the browser its `tools.mcp` names to a page of a listed environment, then to another host
- **THEN** the first goes and the read gate refuses the second, as it refuses a curl there

#### Scenario: Acceptance is off
- **WHEN** `orchestrator.roles.acceptance.enabled` is off
- **THEN** no acceptance turn exists, no acceptance file is read and the session's own deploy check runs as today

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
