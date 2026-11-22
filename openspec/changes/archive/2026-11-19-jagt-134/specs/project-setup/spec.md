## MODIFIED Requirements

### Requirement: A gear on the board opens the settings
The gear's `jagt.yml settings` entry SHALL open one page: projects, review and deploy (`codeReview`, `autoReview`, `infraWait`),
tracker, notifications, and one section for each installed role that contributes keys, Master mode for the Master. A changed key goes
through `ConfigWriter.set` and the toast says what changed. It shows no model setting and no secret value, only env var names.
A role not installed contributes no key: its section is absent and a change naming its key is refused. The project form is the page's, not a second copy.

#### Scenario: The page opens
- **WHEN** the gear is pressed with the Master installed
- **THEN** the page lists projects, review and deploy, tracker, Master mode and notifications, and no model

#### Scenario: The Master is not installed
- **WHEN** the gear is pressed and `roles/master` is absent
- **THEN** the page has no Master mode section, and a change to `roles.master.mode` or `loopBreaker.rounds` is refused

#### Scenario: A key is changed
- **WHEN** auto-review is switched off on the page, `autoReview` being written inline
- **THEN** `autoReview` is a block holding `enabled: false`, and the toast says what changed
