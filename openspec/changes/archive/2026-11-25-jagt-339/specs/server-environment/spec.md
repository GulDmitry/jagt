## ADDED Requirements

### Requirement: A fresh agent home starts past the first-run setup
Provisioning a worktree SHALL set `hasCompletedOnboarding` to true in the agent's `.claude.json`, keeping every other key.
A window still showing a phrase of the runtime's `first-run-prompt` SHALL be reported as waiting on the first-run setup.

#### Scenario: An empty volume
- **WHEN** a task starts and the agent home has no `.claude.json`, or one without the key
- **THEN** the key is true, the other keys are kept, and the session is briefed with no key press

#### Scenario: A first-run picker still shows
- **WHEN** a session's window shows the first-run picker
- **THEN** the session is reported as waiting on the first-run setup
