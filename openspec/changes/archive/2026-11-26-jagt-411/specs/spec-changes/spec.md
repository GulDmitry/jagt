## ADDED Requirements

### Requirement: A defect is investigated before it is fixed
A bug task SHALL record one verdict of a heavy model, analysis only, before its fix is built.
It is one of: the fix holds, a test layer is missing (which), the architecture has a hole (decision or draft).
A Defect-family commit SHALL also carry `Kind`, `Holds` and `Prevents`, per the
[template](../../../docs/defect-investigation.md); `FixedBugCaseFitness` fails one lacking any.
#### Scenario: A fix without its investigation
- **WHEN** a Defect-family commit after the investigation cutoff lacks `Kind`, `Holds` or `Prevents`
- **THEN** the rule test names the commit and the missing trailer
#### Scenario: A fix needing an architecture change
- **WHEN** the fix needs a change to the architecture
- **THEN** its `Holds` names the decision in the commit or the draft opened
