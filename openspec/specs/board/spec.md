# board Specification

## Purpose

The board, jagt's one front-end: what a card shows, which verbs exist, how a command reaches the core.

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

#### Scenario: No project key
- **WHEN** a card shows no project key
- **THEN** the install has one project

### Requirement: Verbs come off the wire in groups
Per-task verbs SHALL come from `Move.actions()`, grouped by `TaskAction.Group` (FLOW moves the task on, TOOL only
looks); the board renders a row per group, read off the wire. A hint's text lives in `command/CommandReference` alone.
A renamed verb keeps its old spelling, advertising only the new, owned by `TaskAction.RENAMED` through `byRetiredVerb`,
read by the palette (`CommandReference.Verb.aliases`) and tier 2.

#### Scenario: Which buttons change something
- **WHEN** the human reads a card's buttons
- **THEN** the top row moves the task on; the bottom row only looks or restarts

#### Scenario: Old spelling
- **WHEN** the human types `review`
- **THEN** `sweep` runs

### Requirement: Commands are two declarations
A verb a task owns SHALL be a `flow/TaskAction` row gated by `Move`, run by `CommandService`; one no task owns is a
`command/GlobalCommand` bean (`command/*`, collected by `GlobalCommands`: id, hint, usage, report or not, about one task
or not) running itself. `CommandReference` renders both: `help`'s text and the palette's verb list.

#### Scenario: Reports
- **WHEN** the human runs `stats`, `master` or `finished`
- **THEN** `stats` gives per task the time on the human, the agent, the code host
- **AND** `master` what the Master judged, and where the human did otherwise: passed then never deployed or reverted,
  failed then deployed
- **AND** `finished` every retired task with its whole status log

### Requirement: One endpoint, one dialog
`GET /api/commands/{id}` SHALL serve any report and `POST` run any other; no command gets its own endpoint, a GET never
starts a task. A report opens in a `<dialog>` over the board, never a page, closing by Escape, its button, or the
dimmed area. One about one task (`aboutOneTask`) gets no bar button, no tier 2 offer, and one line typing into that
session (`POST /api/tasks/say`): not a verb, the only control there.

#### Scenario: Closing
- **WHEN** the human presses Escape, the button, or the dimmed area
- **THEN** the dialog closes

### Requirement: Tier 1 is grammar, tier 2 only proposes
Tier 1, a parsing palette line or a board button, SHALL stay LLM-free. Tier 2, `service/NaturalLanguageDispatch`, sends
⌘K free text via `POST /api/interpret` to a model that only proposes one grammar command. That command is
validated (task and verb exist) and run through `CommandService`: never more than a button. The call is stripped (`--strict-mcp-config
--mcp-config '{"mcpServers":{}}'`, no `--setting-sources`) and answers with the interpretation first.

#### Scenario: A proposal names no real verb
- **WHEN** the model proposes a missing task or verb
- **THEN** nothing runs

### Requirement: Rules live behind the controller
`service/CommandService` SHALL validate against `Move` first; `service/TaskLauncher` starts a task; controller and
palette own no rules. A refusal's sentence is the whole answer; one a caller must act on carries a `flow/Refusal.Code`,
grown only when something branches on it. No tools facade: each MCP tool group declares its own (`surface/mcp/McpTools`
+ `McpToolRegistry`, under `surface/mcp/tools`); `surface/mcp/CallerScope` owns the X-Working-Directory rule.

#### Scenario: A stale tab
- **WHEN** a stale tab sends a verb `Move` no longer allows
- **THEN** it is refused with a sentence, not a git error three layers down

### Requirement: Loopback only
The board SHALL listen on loopback (`server.address: 127.0.0.1`): no password, yet it can deploy, close a task, start an
agent.

#### Scenario: A second machine
- **WHEN** jagt runs with `--server.address=0.0.0.0`
- **THEN** the board is reachable elsewhere, still without a password

### Requirement: Pushed, not polled, acted on by data
The board SHALL NOT poll: `StateService.onChange` is the one event, `TaskEventStream` forwarding it as SSE at
`/api/events` with no payload, which a second serialization could contradict. A periodic tick survives for the ACTIVE
clock. A card carries `data-action`, never a closure: `ui/render` holds the one delegated listener on the grid, so a
card rebuilt under the pointer cannot act for its old task.

#### Scenario: The clock reset
- **WHEN** a card said 17h and the restarted agent shows 0m
- **THEN** that is correct: the clock is time in that status, and a fresh session reports itself anew

### Requirement: A banner leads to its task
A desktop banner SHALL click through to its task (`DesktopNotifier` → `UserNotifier.notify(…, link)`), macOS-only via
`terminal-notifier`'s `-open`, which `MacNotifier` prefers; osascript and `notify-send` drop it, so no caller may depend
on it.

#### Scenario: Clicking a notification
- **WHEN** the human clicks one
- **THEN** the board opens filtered to that task; on Linux the task is in the title instead

### Requirement: Facts sit on the thing they are about
Cards SHALL keep their order; only `order: alias` lets a new task take a retired one's place. The filter box (`/`)
matches alias, ticket number or title; `Esc` clears. The task number opens the ticket, the `MR` chip the request. The
chip shows the request's own age, surviving rounds and restarts, green with ✓ once approved. Its dot: red failed, green
passed, a pulsing ring running, none not read yet. The Deploy button is green while the work is live.

#### Scenario: Taking the colour off
- **WHEN** a live task is reverted with `revert`
- **THEN** the Deploy button loses its green

### Requirement: Only news is on screen
The line under a card SHALL carry news only: NEEDS INPUT, ANSWERED, PROBLEM, NEEDS YOU. The next poll sits in the
tooltip; a stopped poll gets its own mark. A status says itself in words; the highlighted button says what to do. A
badge is the human's move; a quiet "you can …" can wait.

#### Scenario: A deployed task
- **WHEN** a task is deployed
- **THEN** it wears no badge: it waits on nobody, and `done` is the only move left
