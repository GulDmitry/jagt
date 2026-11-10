## ADDED Requirements

### Requirement: A failed check's stop shows its output
A stop set because a check failed (`UNCHANGED_FAILURE`, `ACCEPTANCE_FAILED`) SHALL carry that check's output as its
evidence, which the card shows with the stop. Evidence past `TaskStops.MAX_EVIDENCE` characters is cut and ends with
`[cut]`, so a reader knows the rest was not kept.

#### Scenario: The same failure again
- **WHEN** a hand-back stops as `UNCHANGED_FAILURE` because the tree is the one its verification already failed on
- **THEN** the stop's evidence holds the failing command's output

#### Scenario: Output too long to keep
- **WHEN** a stop's evidence is longer than the cap
- **THEN** it is kept up to the cap and ends with `[cut]`
