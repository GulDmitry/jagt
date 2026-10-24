## MODIFIED Requirements

### Requirement: A stop on a full window arms one wake
With `usageLimits.wakeOnReset: true` (default false, and off when `usageLimits.enabled` is false), a usage-limit stop SHALL
read the limits once. Where a window is at its limit with a known reset, it SHALL arm one wake at the latest such
reset (`SessionWakes`, on the task).

#### Scenario: The window is full
- **WHEN** a session stops on a limit while the 5h window reads 100% and resets at 15:00
- **THEN** a wake is armed for 15:00

#### Scenario: No window is full
- **WHEN** a session stops on a limit while every window reads below 100%, or a full one reports no reset
- **THEN** no wake is armed and the session shows as stopped

### Requirement: A wake says one line, once
At its time a wake SHALL say one line through `SessionHost.nudgeTaskWindow` and nothing else. It is taken off the task
before it is sent, so a stop is woken at most once, across a restart. A report of the session working again SHALL
cancel it.

#### Scenario: The reset passes
- **WHEN** the reset time passes with a wake pending
- **THEN** one line is said to the session, and no later run says it again

#### Scenario: The human has an unsent draft
- **WHEN** the reset passes while the window holds an unsent draft
- **THEN** the wake stays pending and is retried on the next run, never typed onto the draft

#### Scenario: The session moves first
- **WHEN** the session or a human starts a turn before the reset
- **THEN** the wake is cancelled

#### Scenario: A restart between the stop and the reset
- **WHEN** jagt restarts with a wake pending
- **THEN** the wake is still due at the reset
