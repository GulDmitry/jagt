# board-verbs Specification

## Purpose
Which verbs a card offers, how a command reaches the core, and what the palette may propose.

## Requirements

### Requirement: Verbs come off the wire in groups
Per-task verbs SHALL come from `Move.actions()`, a row per `TaskAction.Group`; a hint's text lives in `command/CommandReference` alone.
A renamed verb still answers to its old spelling, unadvertised (`TaskAction.RENAMED`). A hint names the Session tab or a board verb, never `focus`.
#### Scenario: Which buttons change something
- **WHEN** the human reads a card's buttons
- **THEN** the top row moves the task on; the bottom row only looks or restarts

### Requirement: A plan and a repository request have their own verbs
`approve-plan` SHALL replace the CLI plan mode's approval dialog and its go-ahead hint. `add-repo` SHALL replace the answer typed to a session
asking for a repository: highlighted in place of `focus` while that request stands, no mark. Typed `add-repo <ticket> <project>` names the repository
and `remove-repo <ticket> <project>` the one to drop, neither with a button; the project is an optional parameter of the action's one endpoint.
#### Scenario: A plan or a repository waits
- **WHEN** a task is at `PLAN_PENDING`, or its session asked for a configured project it lacks
- **THEN** its top row offers `approve-plan`, or `add-repo` on the NEEDS INPUT card; no other status or question does

### Requirement: Commands are two declarations
A verb a task owns SHALL be a `flow/TaskAction` row gated by `Move`, run by `CommandService`; one no task owns is a `command/GlobalCommand` bean
(`command/*`, collected by `GlobalCommands`) running itself. It declares the board part it opens typed alone and its tab (`offeredOn`).
`CommandReference` renders `help` and the palette's verb list. A launch creating no task is refused.
#### Scenario: Reports
- **WHEN** the human runs `stats`, `master` or `finished`
- **THEN** `stats` splits each open task's time: human, agent, host
- **AND** `stats` counts per task the moves a human made, `BY YOU`
- **AND** `stats` adds a `COLD` column, starts that read no cache as `3/40`, `?` where usage has no cache field
- **AND** `master` the Master's verdicts the human overrode: passed then reverted or never deployed, failed then deployed
- **AND** `finished` every retired task with its status log, their median, the same split
#### Scenario: A verb typed alone
- **WHEN** the human types `do` alone
- **THEN** the launch row takes the focus

### Requirement: One endpoint, one dialog
`GET /api/commands/{id}` SHALL serve any report, `POST` run any other; no command but the launch row's pickers (`POST /api/tasks`) gets its own
endpoint, a GET never starts a task. A report opens in a `<dialog>` over the board, never a page. One about one task (`aboutOneTask`) gets no bar
button, no tier 2 offer, and one line typing into that session (`POST /api/tasks/say`): not a verb, the only control there.
#### Scenario: Closing
- **WHEN** the human presses Escape, the button, or the dimmed area
- **THEN** the dialog closes

### Requirement: Tier 1 is grammar, tier 2 only proposes
Tier 1, a parsing palette line or a board button, SHALL stay LLM-free. Tier 2, `surface/board/NaturalLanguageDispatch`, sends ⌘K free text via `POST
/api/interpret` to a model proposing one grammar command, validated (task and verb exist) and run through `CommandService`: never more than a
button. The call is stripped (`--strict-mcp-config --mcp-config '{"mcpServers":{}}'`, no `--setting-sources`), interpretation first.
#### Scenario: A proposal names no real verb
- **WHEN** the model proposes a missing task or verb
- **THEN** nothing runs

### Requirement: Rules live behind the controller
`service/CommandService` SHALL validate against `Move` first; `service/TaskLauncher` starts a task; controller and
palette own no rules. A refusal's sentence is the whole answer; one a caller acts on carries a `flow/Refusal.Code`, grown only when something
branches on it. No tools facade: each MCP tool group under `surface/mcp/tools` declares its own.
#### Scenario: A stale tab
- **WHEN** a stale tab sends a verb `Move` no longer allows
- **THEN** a sentence refuses it, not a git error

### Requirement: A git case the session can work has a verb
`hand-off` SHALL say the task's standing git cases to its session through `AgentSessions.say`, one block per repository: the task branch, the
shas, the worktree and the command to run. A case is a deploy push the host rejected, a branch diverged from its remote, or a conflict waiting
in a deploy or revert worktree. It is typed, with no button, offered from every started status, and the Master holds it in every mode.
A refusal never tells the human to run git: it names the facts and `hand-off`.
#### Scenario: A deploy push the host rejected
- **WHEN** ABC-42's deploy push was rejected because the deploy branch moved and the human types `hand-off ABC-42`
- **THEN** its session is told the deploy worktree, the branch and `git merge origin/<deploy branch>`
- **AND** the Master in `judge` or `act` may press it too

#### Scenario: Nothing to hand
- **WHEN** `hand-off ABC-42` and no repository of it stands in a git case
- **THEN** it is refused, saying so, and the session is told nothing

### Requirement: Keys reach a session by name
The Session tab SHALL carry a row of keys: Esc, the four arrows, Tab, Space, Enter, 1 to 9, Ctrl-C, Ctrl-U. A press (`POST /api/tasks/key`) names one
`SessionHost.Key`, a fixed set the server validates; the host presses it as a key, never as typed text. The row is no verb.
#### Scenario: A menu is answered
- **WHEN** the human presses Down, then Enter, on a running session's Session tab
- **THEN** each key reaches the session's window once, in that order

#### Scenario: A name outside the set
- **WHEN** a request names a key that is not in the set
- **THEN** it is refused and nothing reaches the session

#### Scenario: No agent runs
- **WHEN** a key is pressed for a task whose window holds no running agent
- **THEN** a sentence says so and nothing is sent

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
