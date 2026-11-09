## ADDED Requirements

### Requirement: Acceptance has an interactive session
A global command `acceptance` and a task verb `scenarios` SHALL open one interactive acceptance session per project, cwd its
workspace, with the same write and shell reach as its headless read. `scenarios` loads the task's ticket and any open
acceptance question. The session ends when its terminal closes, its ending named.

#### Scenario: Scenarios before a deploy
- **WHEN** you press `scenarios` on ABC-42, which has not deployed
- **THEN** an interactive acceptance session opens in the workspace with ABC-42's ticket loaded

#### Scenario: A second session
- **WHEN** an acceptance session already runs for the project and you run `acceptance`
- **THEN** the running one is focused and no second opens
