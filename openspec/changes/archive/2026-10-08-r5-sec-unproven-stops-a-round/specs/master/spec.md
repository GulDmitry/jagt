## RENAMED Requirements

- FROM: `### Requirement: Only blocking and wrong stop a round`
- TO: `### Requirement: Only blocking, wrong and unproven stop a round`

## MODIFIED Requirements

### Requirement: Only blocking, wrong and unproven stop a round
A plan or round SHALL be ready unless a finding is blocking, wrong or unproven, the last a `show:` request; advice
SHALL be a `#` line in `master-review.md`. Its findings SHALL go back to the session, in any mode, and the task to
`IN_PROGRESS`.

#### Scenario: Reviewers find only advice
- **WHEN** findings are only unguarded, noise or an unproven premise
- **THEN** the round is ready

#### Scenario: A round is not ready
- **WHEN** a plan or round has a blocking, wrong or unproven finding
- **THEN** the fix is read anew

#### Scenario: Ready, but its file lists findings
- **WHEN** the verdict is `ready` and `master-review.md` lists findings
- **THEN** it counts as not ready

#### Scenario: The ticket's acceptance check was never run
- **WHEN** a round skipped it
- **THEN** it is not ready, or a question where the check cannot be run

#### Scenario: A round removes something no ticket line asks for
- **WHEN** it removes an endpoint or version unasked
- **THEN** it is a question, not a verdict
