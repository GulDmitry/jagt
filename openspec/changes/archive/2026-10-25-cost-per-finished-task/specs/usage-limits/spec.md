## ADDED Requirements

### Requirement: A finished task keeps what it cost
A `finished.jsonl` row SHALL hold `costUsd` at list price: the task's agent session plus jagt's own reads attributed to it
(`FinishedTask.of`). Where the session's spend was never read, `costUsd` is absent, never 0.
The `stats` report SHALL end with the median `costUsd` of finished tasks that reached DEPLOYED.
A task without one is left out, and `unknown` stands where none has one. It adds no board mark.

#### Scenario: A task whose session was read
- **WHEN** a task with 2.00 USD of session spend and 0.50 USD of own reads is retired
- **THEN** its row holds `costUsd` 2.50

#### Scenario: A task whose session was never read
- **WHEN** a task with own reads but no session spend is retired
- **THEN** its row holds no `costUsd`

#### Scenario: The median
- **WHEN** deployed tasks cost 1.00 and 4.00 and a third has no `costUsd`
- **THEN** `stats` says the median is 2.50 over 2 of 3 deployed tasks

#### Scenario: Nothing known
- **WHEN** no deployed task has a `costUsd`
- **THEN** `stats` says the median is `unknown`
