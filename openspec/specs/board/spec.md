# board Specification

## Purpose

What a card shows, which verbs exist, how a command reaches the core.

## Requirements

### Requirement: One surface, one projection
The board, served by the jar, SHALL be the one front-end. Its projection is `flow/Move` + `flow/TaskView`, built by
`service/TaskViews`, rendered by `/api/tasks`; gate and button read one `FlowRules.allows`;
`TaskViews.snapshot()` reads the configuration once a render. A second surface MUST add no second answer to a board
question. Stopping the backend is no verb.

#### Scenario: Ship
- **WHEN** `ship` runs
- **THEN** it asks its button's `FlowRules`

### Requirement: A mark says what it replaces
The colour legend SHALL be a section of the `help` report (`static/ui/legend.js`), never a control beside it; colours
are [design.md](../../../docs/rules/design.md)'s. A control the board lacks is the bug: the New task form's branch-strategy
picker and MCP schema both read `task/BranchStrategy`. A verb with all its inputs gets no form. The
project key renders only where projects are several, in the install or the task.

#### Scenario: A mark is unclear
- **WHEN** the human opens `Help`
- **THEN** above the commands every mark stands beside its one board-wide meaning

#### Scenario: The launch row
- **WHEN** the human presses New task or `t`
- **THEN** a dialog offers ticket, a box per project, base branch, `plan first`, notes, branch strategy, Start
- **AND** no box ticked sends no project

#### Scenario: Resuming a request
- **WHEN** the human resumes a review request
- **THEN** `resume <url>` after ⌘K is the one control

### Requirement: Verbs come off the wire in groups
Per-task verbs SHALL come from `Move.actions()`, grouped by `TaskAction.Group`, a row per group.
A hint's text lives in `command/CommandReference` alone. A renamed verb still answers to its old spelling (`review`
runs `sweep`), unadvertised (`TaskAction.RENAMED`).

#### Scenario: Which buttons change something
- **WHEN** the human reads a card's buttons
- **THEN** the top row moves the task on; the bottom row only looks or restarts

### Requirement: Commands are two declarations
A verb a task owns SHALL be a `flow/TaskAction` row gated by `Move`, run by `CommandService`; one no task owns is a
`command/GlobalCommand` bean (`command/*`, collected by `GlobalCommands`) running itself. It declares
the board part it opens typed alone and its card (`offeredOn`). `CommandReference` renders `help`
and the palette's verb list. A launch creating no task is refused.

#### Scenario: Reports
- **WHEN** the human runs `stats`, `master` or `finished`
- **THEN** `stats` splits each open task's time: human, agent, host
- **AND** `stats` adds a `COLD` column, starts that read no cache as `3/40`, `?` where usage has no cache field
- **AND** `master` the Master's verdicts the human overrode: passed then reverted or never deployed, failed then
  deployed
- **AND** `finished` every retired task with its status log, their median, the same split

#### Scenario: A verb typed alone
- **WHEN** the human types `do` alone
- **THEN** the launch row takes the focus

### Requirement: One endpoint, one dialog
`GET /api/commands/{id}` SHALL serve any report, `POST` run any other; no command but the launch row's pickers
(`POST /api/tasks`) gets its own endpoint, a GET never starts a task. A report opens in a `<dialog>` over the board,
never a page. One about one task (`aboutOneTask`) gets no bar button, no tier 2 offer, and one line typing into that
session (`POST /api/tasks/say`): not a verb, the only control there.

#### Scenario: Closing
- **WHEN** the human presses Escape, the button, or the dimmed area
- **THEN** the dialog closes

### Requirement: Tier 1 is grammar, tier 2 only proposes
Tier 1, a parsing palette line or a board button, SHALL stay LLM-free. Tier 2,
`surface/board/NaturalLanguageDispatch`, sends ⌘K free text via `POST /api/interpret` to a model proposing one grammar
command, validated (task and verb exist) and run through `CommandService`: never more than a button. The call is
stripped (`--strict-mcp-config
--mcp-config '{"mcpServers":{}}'`, no `--setting-sources`), interpretation first.

#### Scenario: A proposal names no real verb
- **WHEN** the model proposes a missing task or verb
- **THEN** nothing runs

### Requirement: Rules live behind the controller
`service/CommandService` SHALL validate against `Move` first; `service/TaskLauncher` starts a task; controller and
palette own no rules. A refusal's sentence is the whole answer; one a caller acts on carries a `flow/Refusal.Code`,
grown only when something branches on it. No tools facade: each MCP tool group under `surface/mcp/tools` declares
its own.

#### Scenario: A stale tab
- **WHEN** a stale tab sends a verb `Move` no longer allows
- **THEN** a sentence refuses it, not a git error

### Requirement: Loopback only
The board SHALL bind loopback, password-free. `LoopbackFilter` SHALL refuse a foreign Host or Origin and `/mcp`
anything but JSON; `BoardWriteFilter` any write without Origin but `/mcp` and `/api/agent/` as routed.

#### Scenario: A second machine
- **WHEN** jagt runs with `--server.address=0.0.0.0`
- **THEN** it is reachable elsewhere

#### Scenario: A stranger posts
- **WHEN** a foreign page, a rebound name, a session's `curl` or a respelled `/api;x/…` posts a verb
- **THEN** refused 403; `ToolGate` stops the `curl` first

### Requirement: Pushed, not polled, acted on by data
The board SHALL NOT poll: `TaskEventStream` forwards `StateService.onChange`, the one event, as payload-free SSE at
`/api/events` beside a 20s beat. Each connect reads the board once. A periodic tick survives for the ACTIVE clock. A
card carries `data-action`, never a closure, so a rebuilt card cannot act for its old task.

#### Scenario: The backend goes away
- **WHEN** the push connection drops, or misses two beats
- **THEN** the board dims, takes no input and says `backend unreachable` until it reconnects

### Requirement: A banner leads to its task
A desktop banner SHALL click through to its task (`UserNotifier.notify(…, link)`), macOS-only: no caller may depend
on it.

#### Scenario: Clicking a notification
- **WHEN** the human clicks one
- **THEN** the board opens filtered to that task; elsewhere the title names the task

### Requirement: Facts sit on the thing they are about
The task number SHALL open the ticket, the `MR` chip the request; across repositories each project's name opens its
own. The chip shows the request's own age, green with ✓ once approved. The Deploy button is green while the work is
live. A `⚐` beside the title flags the task, in this browser alone.

#### Scenario: Taking the colour off
- **WHEN** a live task is reverted
- **THEN** the Deploy button loses its green

### Requirement: Only news is on screen
The line under a card SHALL carry news only: NEEDS INPUT, ANSWERED, PROBLEM, NEEDS YOU. The next poll or job run sits
in a tooltip; a stopped poll, failed job or stopped `act` gets its own mark. A status says itself in words; the
highlighted button says what to do. A badge is the human's move; a quiet "you can …" can wait.

#### Scenario: A deployed task
- **WHEN** a task is deployed
- **THEN** it wears no badge: `done` is its only move

#### Scenario: Jobs that run fine
- **WHEN** no job's last run failed
- **THEN** no jobs chip shows

### Requirement: Components ship as the files that run
The board SHALL render Preact components written with `htm`, served from `static/vendor/` byte for byte as the npm
registry published them. The page's import map resolves them. A page SHALL load nothing from outside the jar, and
the board's code and styles SHALL weigh under 160 KB (`BoardAssetsTest`).

#### Scenario: A vendored file is edited
- **WHEN** `vendor/preact.mjs` differs from the published 11.0.1 by one byte
- **THEN** `BoardAssetsTest` fails naming the file

#### Scenario: A page reaches for a CDN
- **WHEN** a page under `static/` loads a script or a stylesheet from `https://`
- **THEN** `BoardAssetsTest` fails

### Requirement: The styleguide draws every state
`styleguide.html` SHALL render each component in each state its `styleguide/` fixtures name, with the modules the
board renders. A component reads no module state: everything it shows arrives in its props.

#### Scenario: Opening the styleguide
- **WHEN** a human opens `/styleguide.html`
- **THEN** every card state stands as one card under its name, and the page throws nothing

### Requirement: A press is logged as a control and a status
The board SHALL log each press as `{at, control, status}` to `interactions.jsonl` beside `state.json`, unless
`interactionLog: false`. `control` names the verb, report or element; `status` is the task's status or null. A press
SHALL NOT carry a task id, an alias, a ticket or typed text. A batch is sent two seconds after a burst begins, holds
at most 50 presses, and is refused whole when one press breaks the shape.

#### Scenario: Pressing Focus
- **WHEN** the human presses Focus on an agent's working task
- **THEN** a line holds `action:focus` and `IN_PROGRESS`, and nothing naming the task

#### Scenario: A malformed press
- **WHEN** a batch carries a control with a space, a status in lower case, or a date a day away
- **THEN** it is refused with a sentence, and nothing of it is written

#### Scenario: Switched off
- **WHEN** `interactionLog: false`
- **THEN** a batch is checked and nothing is written
