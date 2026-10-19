## ADDED Requirements

### Requirement: A usage-limit stop is a typed report
A turn the CLI ends on a usage limit SHALL be reported by its own hook event, declared in
`hooks/<runtime>.properties` as `rate-limit-stop` with its `rate-limit-matcher`, a POST to `/api/agent/session/rate-limit`.
jagt SHALL never infer a limit from what a window shows.

#### Scenario: The CLI ends a turn on a rate limit
- **WHEN** the CLI fires its failure event with the rate-limit cause
- **THEN** the session is recorded as stopped, as for any ended turn

#### Scenario: A runtime declares no such event
- **WHEN** `hooks/<runtime>.properties` has no `rate-limit-stop`
- **THEN** no hook is written for it
