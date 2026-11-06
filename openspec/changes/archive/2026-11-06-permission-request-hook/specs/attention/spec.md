## MODIFIED Requirements

### Requirement: A hook reports, the watchdog decides
A blocked prompt MUST come from the runtime's typed `permission-request` event, never from wording: a Notification is always
`idle`. The report is answered with nothing, so the CLI shows its own dialog. The stop SHALL be the reducer's
alone, on the transition only, cleared by any report (`withStatus`), a later alive sign winning by arrival.
#### Scenario: A stamped task reports
- **WHEN** any report arrives
- **THEN** the stop clears
#### Scenario: A permission prompt opens
- **WHEN** the CLI opens a permission prompt for `Bash`
- **THEN** NEEDS INPUT names `permission to use Bash`
- **WHEN** a Notification says "needs your permission"
- **THEN** the session is only idle
