# cost-per-accepted-task Specification

## Purpose
What a shipped task really cost: all spend of a week's finished tasks over those that shipped.

## Requirements

### Requirement: A week's cost per accepted task counts every task's spend
`CostPerAcceptedTask.over` SHALL sum the `costUsd` of every task finished in the week, failed, stopped and reworked included.
It SHALL divide that by the number of those tasks that reached DEPLOYED. The week is seven days from the start given to it.
A task without `costUsd` adds zero and counts only if it shipped. A week with no shipped task has no number.

#### Scenario: Spend of tasks that did not ship
- **WHEN** a week finished a shipped task costing 2.00 and a task costing 3.00 that never reached DEPLOYED
- **THEN** the cost per accepted task is 5.00

#### Scenario: A task with no recorded cost
- **WHEN** a finished task of the week has no `costUsd`
- **THEN** it adds zero to the spend and counts in the denominator only if it shipped

### Requirement: A week's cost per accepted task is read in stats
`stats` SHALL end with it for the last 7 days, `≥` before a lower bound, `unknown` where none shipped. It adds no board mark.
A week's number is a lower bound where a finished task of it has no known `costUsd`, or an unpriced call.

#### Scenario: A lower bound
- **WHEN** `stats` is read and a task finished in the last 7 days has no `costUsd`
- **THEN** its line reads `cost per accepted task, last 7 days: ≥ 4.00 USD (1 shipped of 2 finished)`

#### Scenario: Nothing shipped
- **WHEN** no task finished in the week reached DEPLOYED
- **THEN** there is no number, never a division by zero, and `stats` says `unknown (0 shipped of 3 finished)`
