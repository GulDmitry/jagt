## MODIFIED Requirements

### Requirement: One task runs every fitness function and nothing else
`./gradlew fitness` SHALL run every fitness function: rows, `Fitness` classes and tool rows.
It SHALL print each one's value and threshold, and run one block of the reserved tree when given one.
A function a suite run measures SHALL print its threshold and the last value a check measured, or `no value yet`.
#### Scenario: A block is given
- **WHEN** the task gets one block of the reserved tree
- **THEN** only that block's fitness functions run
#### Scenario: A suite-measured function was never measured
- **WHEN** a function is measured by a suite run and no check has measured it
- **THEN** the task prints its threshold and `no value yet`
#### Scenario: A suite-measured function was measured
- **WHEN** a check measured the function at 5
- **THEN** the task prints its threshold and `last value 5`

## ADDED Requirements

### Requirement: The test task reads every tracked file
The plugin SHALL declare every tracked file as an input of the `test` task.
A cached run then cannot hide a breach.
#### Scenario: A tracked file changes
- **WHEN** a tracked file changes and nothing else does
- **THEN** the `test` task runs again

### Requirement: A tool that fails fails the check
A tool that exits with a failure code SHALL fail its check; its error stream alone does not, as a scanner logs progress there.
The `ToolFailureException` names the tool, the exit code and the error stream.
The vulnerability scan counts exit code 1 as findings found.
#### Scenario: The tool crashes
- **WHEN** the scanner exits with 127
- **THEN** the check fails with a `ToolFailureException` naming the exit code
#### Scenario: The tool logs to its error stream
- **WHEN** the scanner exits 0 and writes `scanning` to its error stream
- **THEN** the check measures the findings it printed
