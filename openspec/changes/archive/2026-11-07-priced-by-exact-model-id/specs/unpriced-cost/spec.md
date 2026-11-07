## ADDED Requirements

### Requirement: A model is priced by its exact id
A session turn SHALL be priced only when its model id equals a price row's id, or that id plus a `-YYYYMMDD` date suffix.
Any other id, one merely containing a known name included, SHALL be unpriced.

#### Scenario: A model id merely containing a known name is unpriced
- **WHEN** a session log holds a turn of a model whose id merely contains a known model's name
- **THEN** `unpricedCalls` is 1 and `costUsd` is unchanged

#### Scenario: A dated variant of a known id
- **WHEN** a session log holds a turn of a known id followed by `-YYYYMMDD`
- **THEN** it is priced at that id's row
