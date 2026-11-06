## ADDED Requirements

### Requirement: A project bounds its verify command
The verify command SHALL be stopped after the project's `verifyMinutes`, twenty minutes where none is written or it is not positive.
#### Scenario: A project with a limit
- **WHEN** a project has `verifyMinutes: 5` and its verify command runs at `VERIFYING`
- **THEN** the command is stopped after 5 minutes

#### Scenario: A project with no limit
- **WHEN** a project has no `verifyMinutes`
- **THEN** its verify command is stopped after 20 minutes
