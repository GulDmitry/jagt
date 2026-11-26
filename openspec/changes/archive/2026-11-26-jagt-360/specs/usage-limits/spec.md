## MODIFIED Requirements

### Requirement: A stop on a full window arms one wake
With `usageLimits.wakeOnReset: true` (default false, and off when `usageLimits.enabled` is false), a usage-limit stop SHALL
read the limits once. Where every window at its limit has a known reset, it SHALL arm one wake at the latest such
reset (`SessionWakes`, on the task). Where any full window has no reset time, no wake SHALL be armed.

#### Scenario: The window is full
- **WHEN** a session stops on a limit while the 5h window reads 100% and resets at 15:00
- **THEN** a wake is armed for 15:00

#### Scenario: No window is full, or a full one has no reset time
- **WHEN** a session stops on a limit while every window reads below 100%, or a full window has no reset time, whatever the others reset at
- **THEN** no wake is armed and the session shows as stopped
