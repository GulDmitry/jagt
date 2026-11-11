## MODIFIED Requirements

### Requirement: A shell line runs in a session behind the CLI's escape
`run` SHALL replace typing the CLI's shell escape into the say field: a `Move.actions()` row of group `session`, drawn on the Session tab
beside Send and on no card. The line is typed behind the escape `AgentRuntime.shellEscape` names, into a warm or a cold session alike, never relayed
as words under the brief. A runtime naming none offers no `run`. The Master holds it like every human verb (`press_task`, its line in `line`,
never in `project`); a verb taking no line refuses one, and `run` refuses a project.
The log keeps that a line ran and its length, never the line.
#### Scenario: A warm session
- **WHEN** the human runs `ls` on a running Claude session's Session tab
- **THEN** `!ls` is typed into its window and Enter pressed

#### Scenario: A cold session
- **WHEN** the session has lost its cache and the human runs `ls`
- **THEN** `!ls` is typed into its window; `task_context.md` is not written

#### Scenario: A CLI without a shell escape
- **WHEN** the session's CLI names no shell escape
- **THEN** the task offers no `run` and the Session tab shows no Run button

#### Scenario: What the log keeps
- **WHEN** a line runs
- **THEN** one log line names the task and the line's length, and holds no part of the line

#### Scenario: The line has its own argument
- **WHEN** `run` is pressed with a line
- **THEN** the line arrives as `line` and `project` stays empty

#### Scenario: A project is not a line
- **WHEN** a project is named for `run`, or a line for any other verb
- **THEN** it is refused and nothing is written
