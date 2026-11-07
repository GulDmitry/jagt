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

### Requirement: A model is priced by its exact id
A session turn SHALL be priced only when its model id equals a price row's id, or that id plus a `-YYYYMMDD` date suffix.
Any other id, one merely containing a known name included, SHALL be unpriced.

#### Scenario: A model id merely containing a known name is unpriced
- **WHEN** a session log holds a turn of a model whose id merely contains a known model's name
- **THEN** `unpricedCalls` is 1 and `costUsd` is unchanged

#### Scenario: A dated variant of a known id
- **WHEN** a session log holds a turn of a known id followed by `-YYYYMMDD`
- **THEN** it is priced at that id's row

### Requirement: A Codex session's turns are booked unpriced
With `orchestrator.agent.cli=codex`, each `token_count` event of the session's newest rollout under the worktree's
Codex home that carries usage SHALL book one call: fresh input (input minus cached), cached input and output tokens.
No Codex price is known, so the call is unpriced. An agent CLI keeping no log jagt reads books nothing.

#### Scenario: A Codex turn
- **WHEN** the agent CLI is codex and its rollout holds a `token_count` turn with usage
- **THEN** its tokens are booked, cached input apart from fresh input, as one unpriced call

#### Scenario: A token count without usage
- **WHEN** a `token_count` event carries no usage
- **THEN** nothing is booked for it
