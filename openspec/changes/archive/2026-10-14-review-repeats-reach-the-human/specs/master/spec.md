## MODIFIED Requirements

### Requirement: Only blocking, wrong and unproven stop a round
A plan or round SHALL be ready unless a finding is blocking, wrong or unproven, the last a `show:` request; advice
SHALL be a `#` line in `master-review.md`. Its findings SHALL go back to the session, in any mode, and the task to
`IN_PROGRESS`, until rounds of repeats reach the human (`FindingMatch`).

#### Scenario: Reviewers find only advice
- **WHEN** findings are only unguarded, noise or an unproven premise
- **THEN** the round is ready

#### Scenario: A round is not ready
- **WHEN** a plan or round has a blocking, wrong or unproven finding
- **THEN** the fix is read anew; one an earlier round made on the same file goes back `[repeat]`
- **AND** where two rounds running hold only repeats, the second is your question, in any mode

#### Scenario: Ready, but its file lists findings
- **WHEN** the verdict is `ready` and `master-review.md` lists findings
- **THEN** it counts as not ready

#### Scenario: The acceptance check never ran
- **WHEN** a round skipped it
- **THEN** it is not ready, or a question where the check cannot be run

#### Scenario: A round removes what no ticket line asks
- **WHEN** it removes an endpoint or version unasked
- **THEN** it is a question, not a verdict
