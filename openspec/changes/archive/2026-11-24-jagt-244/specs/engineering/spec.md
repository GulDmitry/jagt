## MODIFIED Requirements

### Requirement: Every test run ends in mutants on the diff
Every test run SHALL end in mutants on the diff, each survivor judged. The same diff SHALL mutate once.
#### Scenario: A changed line is mutated
- **WHEN** a main line changes **THEN** `pitestDiff` ends every `test` listing the survivors on it; the board's JS: Stryker on demand
#### Scenario: The same diff runs again
- **WHEN** `pitestDiff` runs on a diff it already judged **THEN** `pitestDiffSurvivors` is UP-TO-DATE and prints the stored survivors
- **AND** a changed line or class bytecode mutates again
