## MODIFIED Requirements

### Requirement: Only blocking, wrong and unproven stop a round
A plan or round SHALL be ready unless a finding is blocking, wrong or unproven, the last a `show:` request. Only
correctness or a stated requirement SHALL stop one; wording, glossary and style SHALL be noise. Advice SHALL be a `#`
line in `master-review.md`. Its findings SHALL go back to the session, in any mode, and the task to `IN_PROGRESS`,
until the round ceiling stops it; a finding is the same by file, anchor and kind (`MasterDecisions`).

#### Scenario: Only advice
- **WHEN** findings are only unguarded, noise or an unproven premise
- **THEN** the round is ready

#### Scenario: A bug test red for another reason
- **WHEN** a bug fix's test fails before the fix, but not with the signal the report names
- **THEN** it is wrong, and the round is not ready

#### Scenario: A round is not ready
- **WHEN** a plan or round has a blocking, wrong or unproven finding
- **THEN** the fix is read anew; one with an earlier round's file, anchor and kind goes back `[repeat]`, reworded or not
- **AND** a round of only repeats is a round like any, toward the ceiling that stops the task for you, never a question
- **AND** a verdict `ready` beside findings counts as not ready

#### Scenario: The acceptance check skipped
- **WHEN** a round skipped it
- **THEN** it is not ready, or a question where it cannot be

#### Scenario: An unasked removal
- **WHEN** it removes an endpoint or version unasked
- **THEN** it is a question, not a verdict
