## MODIFIED Requirements

### Requirement: A usage-limit stop is a typed report
A turn the CLI ends on an API error SHALL be reported by its own hook event, whatever the error. `hooks/<runtime>.properties`
SHALL declare it as `turn-failure`, a POST to `/api/agent/session/turn-failure`, and the error word meaning a usage limit as
`rate-limit-error`. The failure whose error equals `rate-limit-error` SHALL arm the wake. jagt SHALL never infer a limit
from what a window shows.

#### Scenario: The CLI ends a turn on a rate limit
- **WHEN** the CLI fires its failure event with the rate-limit error
- **THEN** the session is recorded as a failed turn naming that error, and the wake is armed

#### Scenario: The CLI ends a turn on another error
- **WHEN** the CLI fires its failure event with an error other than `rate-limit-error`
- **THEN** the session is recorded as a failed turn naming that error, and no wake is armed

#### Scenario: A runtime declares no such event
- **WHEN** `hooks/<runtime>.properties` has no `turn-failure`
- **THEN** no hook is written for it
