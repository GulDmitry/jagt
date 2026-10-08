# board Specification

## Purpose

What a card shows, which verbs exist, how a command reaches the core.

## Requirements

### Requirement: One surface, one projection
The board, served by the jar, SHALL be the one front-end. Its projection is `flow/Move` + `flow/TaskView`, built by
`service/TaskViews`, rendered by `/api/tasks`; gate and button read one `FlowRules.allows`;
`TaskViews.snapshot()` reads the configuration once per render. A second surface MUST add no second answer to a board
question. Stopping the backend is no verb.

#### Scenario: Ship
- **WHEN** `ship` runs
- **THEN** it asks its button's `FlowRules`

### Requirement: A mark says what it replaces
The colour legend SHALL be a section of the `help` report (`static/ui/legend.js`), never a control beside it; colours
are [design.md](../../../docs/rules/design.md)'s. A control the board lacks is the bug: the launch row's branch-strategy
sentence, picker and MCP schema all read `task/BranchStrategy`. The project key renders only where an install has
several projects, or a task spans several.

#### Scenario: A mark is unclear
- **WHEN** the human opens `Help`
- **THEN** above the commands every mark stands beside its one board-wide meaning

#### Scenario: The launch row
- **WHEN** the human starts a task there
- **THEN** it offers ticket, project, base branch, `plan first`, notes, branch strategy, Start
- **AND** an untouched picker sends nothing

### Requirement: Verbs come off the wire in groups
Per-task verbs SHALL come from `Move.actions()`, grouped by `TaskAction.Group`; the board renders a row per group,
read off the wire. A hint's text lives in `command/CommandReference` alone.
A renamed verb still answers to its old spelling, advertised nowhere (`TaskAction.RENAMED`, read by the palette and
tier 2).

#### Scenario: Which buttons change something
- **WHEN** the human reads a card's buttons
- **THEN** the top row moves the task on; the bottom row only looks or restarts

#### Scenario: Old spelling
- **WHEN** the human types `review`
- **THEN** `sweep` runs

### Requirement: Commands are two declarations
A verb a task owns SHALL be a `flow/TaskAction` row gated by `Move`, run by `CommandService`; one no task owns is a
`command/GlobalCommand` bean (`command/*`, collected by `GlobalCommands`: id, hint, usage, report or not, about one task
or not, the board part it opens typed alone) running itself. `CommandReference` renders both: `help`'s text and the
palette's verb list. A launch that creates no task is refused.

#### Scenario: Reports
- **WHEN** the human runs `stats`, `master` or `finished`
- **THEN** `stats` gives per task the time on the human, the agent, the code host
- **AND** `master` what the Master judged, and where the human did otherwise: passed then reverted or never deployed,
  failed then deployed
- **AND** `finished` every retired task with its status log

#### Scenario: A verb typed alone
- **WHEN** the human types `do` alone
- **THEN** the launch row takes the focus

### Requirement: One endpoint, one dialog
`GET /api/commands/{id}` SHALL serve any report and `POST` run any other; no command gets its own endpoint, a GET never
starts a task. A report opens in a `<dialog>` over the board, never a page. One about one task (`aboutOneTask`) gets no bar button, no tier 2 offer, and one line typing into that
session (`POST /api/tasks/say`): not a verb, the only control there.

#### Scenario: Closing
- **WHEN** the human presses Escape, the button, or the dimmed area
- **THEN** the dialog closes

### Requirement: Tier 1 is grammar, tier 2 only proposes
Tier 1, a parsing palette line or a board button, SHALL stay LLM-free. Tier 2, `service/NaturalLanguageDispatch`, sends
⌘K free text via `POST /api/interpret` to a model proposing one grammar command, validated (task and verb exist) and
run through `CommandService`: never more than a button. The call is stripped (`--strict-mcp-config
--mcp-config '{"mcpServers":{}}'`, no `--setting-sources`) and answers with the interpretation first.

#### Scenario: A proposal names no real verb
- **WHEN** the model proposes a missing task or verb
- **THEN** nothing runs

### Requirement: Rules live behind the controller
`service/CommandService` SHALL validate against `Move` first; `service/TaskLauncher` starts a task; controller and
palette own no rules. A refusal's sentence is the whole answer; one a caller must act on carries a `flow/Refusal.Code`,
grown only when something branches on it. No tools facade: each MCP tool group under `surface/mcp/tools` declares
its own; `surface/mcp/CallerScope` owns the X-Working-Directory rule.

#### Scenario: A stale tab
- **WHEN** a stale tab sends a verb `Move` no longer allows
- **THEN** a sentence refuses it, not a git error

### Requirement: Loopback only
The board SHALL bind loopback and ask no password; `surface/board/LoopbackFilter` SHALL refuse a foreign Host or
Origin, and `/mcp` anything but JSON.

#### Scenario: A second machine
- **WHEN** jagt runs with `--server.address=0.0.0.0`
- **THEN** the board is reachable elsewhere, still without a password

#### Scenario: Another site posts a verb
- **WHEN** a page elsewhere, or a rebound name, sends one
- **THEN** it is refused 403

### Requirement: Pushed, not polled, acted on by data
The board SHALL NOT poll: `StateService.onChange` is the one event, `TaskEventStream` forwarding it as SSE at
`/api/events` with no payload. Each connect reads the board once. A periodic tick survives for the ACTIVE clock. A card
carries `data-action`, never a closure, so a card rebuilt under the pointer cannot act for its old task.

#### Scenario: The clock reset
- **WHEN** a card said 17h and the restarted agent shows 0m
- **THEN** that is correct: the clock is time in that status

#### Scenario: The backend goes away
- **WHEN** the push connection drops
- **THEN** the board dims, takes no input and says `backend unreachable` until it reconnects

### Requirement: A banner leads to its task
A desktop banner SHALL click through to its task (`UserNotifier.notify(…, link)`), macOS-only via `terminal-notifier
-open`; osascript and `notify-send` drop it, so no caller may depend on it.

#### Scenario: Clicking a notification
- **WHEN** the human clicks one
- **THEN** the board opens filtered to that task; on Linux the task is in the title instead

### Requirement: Facts sit on the thing they are about
Cards SHALL keep their order; only `order: alias` lets a new task take a retired one's place. The filter box (`/`)
matches alias, ticket number or title; `Esc` clears. The task number opens the ticket, the `MR` chip the request; across
repositories each project's name opens its own. The chip shows the request's own age, green with ✓ once approved. Its
dot: red failed, green passed, a pulsing ring running, none not read yet. The Deploy button is green while the work is
live.

#### Scenario: Taking the colour off
- **WHEN** a live task is reverted
- **THEN** the Deploy button loses its green

### Requirement: Only news is on screen
The line under a card SHALL carry news only: NEEDS INPUT, ANSWERED, PROBLEM, NEEDS YOU. The next poll or job run sits
in a tooltip; a stopped poll and a failed job get their own mark. A status says itself in words; the highlighted button
says what to do. A badge is the human's move; a quiet "you can …" can wait.

#### Scenario: A deployed task
- **WHEN** a task is deployed
- **THEN** it wears no badge: it waits on nobody, and `done` is the only move left

#### Scenario: Jobs that run fine
- **WHEN** no job's last run failed
- **THEN** the header shows no jobs chip
