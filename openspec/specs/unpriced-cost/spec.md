# unpriced-cost Specification

## Purpose
A cost jagt cannot know is a lower bound, never zero.

## Requirements

### Requirement: An unknown price is a lower bound, never zero
A call of unknown cost SHALL count in `unpricedCalls` and add nothing to `costUsd`; a reported cost of 0 stays priced.
Unknown cost means a session turn naming a model with no price row, or a headless envelope or step reporting none.
A total with `unpricedCalls` above 0 SHALL print `≥` in `stats`, `/master` and the median cost per deployed task.
A record without `unpricedCalls` reads as 0. Each unpriced model SHALL log one WARN per process. It adds no board mark.

#### Scenario: A model with no known price
- **WHEN** a session log holds a turn of a model no price row names
- **THEN** its tokens count, `unpricedCalls` is 1, `costUsd` is unchanged and `stats` prints `≥` before the total

#### Scenario: The same model twice
- **WHEN** a session log holds two turns of one unpriced model
- **THEN** one WARN `model unpriced` names it

#### Scenario: A record written before unpriced calls were counted
- **WHEN** a ledger line has no `unpricedCalls`
- **THEN** it reads with 0 and its cost is complete
