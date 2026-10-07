## ADDED Requirements

### Requirement: A second wording of a rule merges into it
When a written rule leaves `memory/routing.md` at its ceiling, one read of the whole file SHALL name two rules
placing the same items (`MasterAssistant.sameRules`). jagt SHALL merge them only where both place in one project,
adding their counts and dating the dropped wording (`RoutingMemory.merge`).

#### Scenario: Two wordings place in one project
- **WHEN** the read names two rules placing in one project
- **THEN** the dropped one's count joins the kept one's, and it stays as a dated line

#### Scenario: The read names rules placing in different projects
- **WHEN** the two rules place in different projects
- **THEN** both stand
