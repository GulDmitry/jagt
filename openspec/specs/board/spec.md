# board Specification

## Purpose

What a card shows and the surface it is drawn on; the verbs are [`board-verbs`](../board-verbs/spec.md).

## Requirements

### Requirement: One surface, one projection
The board, an app of its own in `board/`, SHALL be the one front-end. Its projection is `flow/Move` + `flow/view/TaskView`, built by
`service/task/TaskViews`, rendered by `/api/tasks`; gate and button read one `FlowRules.allows`; `TaskViews.snapshot()` reads the
configuration once a render. A second surface MUST add no second answer to a board question. Stopping the backend is no verb.
#### Scenario: Ship
- **WHEN** `ship` runs; **THEN** it asks its button's `FlowRules`

### Requirement: A mark says what it replaces
The colour legend SHALL be a section of the `help` report (`board/ui/legend.js`), never a control beside it; colours are
[design.md](../../../docs/rules/design.md)'s. A control the board lacks is the bug: New task form's branch-strategy picker and MCP schema both read
`task/repo/BranchStrategy`. A verb with all its inputs gets no form. The project key shows only where projects are several, in the install or task.
#### Scenario: A mark is unclear
- **WHEN** the human opens `Help`; **THEN** above the commands every mark stands beside its one board-wide meaning
#### Scenario: The launch row
- **WHEN** the human presses New task or `t`
- **THEN** a dialog offers ticket, a box per project, base branch, `plan first / project unknown`, notes, branch strategy, Start
#### Scenario: A project is hovered in the launch row
- **WHEN** the human hovers a project's name or box
- **THEN** its hover shows the project's whole `jagt.yml` entry and the address of its repository's remote
#### Scenario: Start with no project
- **WHEN** the human presses Start with no box ticked and `plan first / project unknown` unticked
- **THEN** the form says "Choose a project, or start with plan first" and sends nothing
- **AND** it says the server's other refusals itself: spaces in the ticket, no ticket and no notes, notes alone and no project of several
- **WHEN** `plan first / project unknown` is ticked; **THEN** Start sends the task with no project, and no router is asked
#### Scenario: Resuming a request
- **WHEN** the human resumes a review request; **THEN** `resume <url>` after ⌘K is the one control

### Requirement: Pushed, not polled, acted on by data
The board SHALL NOT poll: `TaskEventStream` forwards `StateWritten` as payload-free SSE at
`/api/events`, and a role's new log line as `role-log` naming the role, beside a 20s beat. Each connect reads the board once.
A periodic tick survives for the ACTIVE clock. A card carries `data-action`, never a closure, so a rebuilt card cannot act for its old task.
#### Scenario: The backend goes away
- **WHEN** the push connection drops, or misses two beats; **THEN** the board dims, takes no input and says `backend unreachable` until it reconnects

### Requirement: A banner leads to its task
A desktop banner SHALL click through to its task (`UserNotifier.notify(…, link)`), macOS-only: no caller may depend on it.
#### Scenario: Clicking a notification
- **WHEN** the human clicks one
- **THEN** the board opens filtered to that task; elsewhere the title names the task
- **WHEN** `ABC-42`, shown on its card as `p3`, stops
- **THEN** the banner reads `jagt · p3 · ABC-42 · agent stopped`; a task without a short name reads `jagt · ABC-42 · agent stopped`

### Requirement: Facts sit on the thing they are about
The task number SHALL open the ticket. Where the board names projects, each project's name opens its own request; on a board of one project the
`request` chip does. The chip shows the request's own age, green with ✓ once approved. Its hover says how often the task shipped, from the second
ship on. The Deploy button is green while the work is live. A `⚐` beside the title flags the task, in this browser alone.
#### Scenario: Taking the colour off
- **WHEN** a live task is reverted; **THEN** the Deploy button loses its green
#### Scenario: A one-repository card on a board of several projects
- **WHEN** ABC-42 in `alpha`, its request open 8h, shares the board with a task in `beta`
- **THEN** its card reads `alpha 8h`, the name opening the request, and no `request` word
#### Scenario: A task shipped again
- **WHEN** ABC-42 shipped twice; **THEN** its request chip's hover reads `shipped 2 times`

### Requirement: A plan shows what changed since its last approval
Each `approve-plan` SHALL keep the approved `plan.md` as the task's baseline, in jagt's own root, not the worktree.
The Artifacts tab SHALL render `plan.md` against that baseline: a line new or changed is bold with a bar,
a removed line struck through in the muted colour where it stood. No approved plan, no marks. No other document is marked.
#### Scenario: No plan approved yet
- **WHEN** ABC-42's plan is open in the Artifacts tab and no `approve-plan` ran; **THEN** no line is marked and none is struck
#### Scenario: Changed after the approval
- **WHEN** ABC-42's plan was approved, then a line was rewritten and another deleted
- **THEN** the rewritten line is marked, the deleted one is shown struck through, and every other line is plain
#### Scenario: Approved again
- **WHEN** the changed plan is approved; **THEN** it becomes the baseline and the next read of the plan marks nothing

### Requirement: Only news is on screen
The line under a card SHALL carry news only: NEEDS INPUT, ANSWERED, PROBLEM, NEEDS YOU. The next poll or job run sits
in a tooltip; a stopped poll, failed job or stopped `act` gets its own mark. A status says itself in words; the
highlighted button says what to do. A badge is the human's move; a quiet "you can …" can wait.
#### Scenario: A deployed task
- **WHEN** a task is deployed
- **THEN** it wears no badge: `done` is its only move
- **AND** while no job's last run failed, no jobs chip shows

### Requirement: Components ship as the files that run
The board SHALL render Preact components written with `htm`, served from `board/vendor/` byte for byte as the npm registry published them. The page's
import map resolves them. A page SHALL load nothing from outside the jar, and the board's code and styles SHALL weigh under 236 KB
(`BoardAssetsTest`). Its `manifest.webmanifest` and two PNG icons, served from the jar, make it installable as an app from the URL the banner prints.
#### Scenario: A shipped file is not what ships
- **WHEN** `vendor/preact.mjs` differs from the published 11.2.0 by one byte
- **THEN** `BoardAssetsTest` fails naming the file
- **WHEN** a page under `board/` loads a script or a stylesheet from `https://`
- **THEN** `BoardAssetsTest` fails
- **WHEN** a human installs the board from Chrome or Safari
- **THEN** it opens in its own window at `/`, and `GET /manifest.webmanifest` answers `application/manifest+json`

### Requirement: The styleguide draws every state
`styleguide.html` SHALL render each component in each state its `styleguide/` fixtures name, with the modules the
board renders. A component reads no module state: everything it shows arrives in its props.
#### Scenario: Opening the styleguide
- **WHEN** a human opens `/styleguide.html`; **THEN** every card state stands as one card under its name, and the page throws nothing

### Requirement: A press is logged as a control and a status
The board SHALL log each press as `{at, control, status}` to `interactions.jsonl` beside `state.json`, unless `interactionLog: false`. `control`
names the verb, report or element; `status` is the task's status or null. A press SHALL NOT carry a task id, an alias, a ticket or typed text. A
batch is sent two seconds after a burst begins, holds at most 50 presses, and is refused whole when one press breaks the shape.
#### Scenario: Pressing Focus
- **WHEN** the human presses Focus on an agent's working task; **THEN** a line holds `action:focus` and `IN_PROGRESS`, and nothing naming the task
#### Scenario: A press refused or switched off
- **WHEN** a batch carries a control with a space, a status in lower case, or a date a day away
- **THEN** it is refused with a sentence, and nothing of it is written
- **WHEN** `interactionLog: false`; **THEN** a batch is checked and nothing is written

### Requirement: The press log notes two sightings
The board SHALL log `shown:required` when a task's attention BECOMES required, and `shown:jobs-failed` when the failed-jobs chip appears. Both SHALL
go through a press's batch and switch. A sighting is logged once per change, never per render, and the first board painted only seeds.
`shown:required` carries the task's status and `shown:jobs-failed` null; neither carries a task id or text.
#### Scenario: A sighting
- **WHEN** a task's attention turns REQUIRED, or the hidden failed-jobs chip appears, on a board already painted
- **THEN** one line holds `shown:required` and its status, or `shown:jobs-failed` and null; further renders add none

### Requirement: Every window jagt opens is on the Session tab
A window jagt opens SHALL show on the Session tab and take `say`, whether a task's session or not: a login and the
acceptance scenarios session. The tab lists the open task's session first, then every such window by name.
#### Scenario: A window that is no task's
- **WHEN** jagt opens a login or the scenarios window
- **THEN** the Session tab lists it, streams its terminal live, and a line typed under it reaches that window

### Requirement: Loopback, or behind a password login
`orchestrator.auth` SHALL be `none` (default: loopback, no login), `password` (a bcrypt hash, never logged) or `oidc` (`oidc-login`).
`none` with a `server.address` beyond loopback, `auth.allowedHosts` or a non-loopback `server.publicUrl` refuses the start naming `auth`.
`password` guards the board, `/api` and `/api/events`: a browser is sent to `/login.html`, `/api` answers 401. `/mcp` and `/api/agent/` take no login;
off loopback or forwarded, they answer 401 without the Master token.
#### Scenario: No login
- **WHEN** `auth` is `password` and a browser opens the board without logging in; **THEN** it is sent to the login page and `/api` answers 401
#### Scenario: A session
- **WHEN** `auth` is `password` and a session calls `/mcp` with the worktree header
- **THEN** it is served with no login from loopback, and refused 401 off loopback or forwarded without the Master token
#### Scenario: A second machine
- **WHEN** `auth` is `none` and `server.address` is not loopback, or `server.publicUrl` is `https://jagt.example.com`
- **THEN** the start is refused naming `auth`

### Requirement: A lost reach shows in the board's head
Where jagt cannot reach a code host, an MCP server or the release feed, the header SHALL show ONE mark naming driver
and host. Its hover gives the fix command and the next probe's time, if scheduled. It replaces the per-card `DEPLOY_UNREAD`
stop on that cause and wears the broken colour. `login` SHALL open the driver's login as a Session tab window's process, never a keystroke.
#### Scenario: The code host's CLI is signed out
- **WHEN** the code host's CLI is signed out of `git.example.invalid` while ABC-42 and ABC-43 are deployed
- **THEN** no card stops; the header says the CLI is not logged in there, its hover the driver's login command
#### Scenario: The human logs in
- **WHEN** the human presses the mark and signs in; **THEN** the next deploy read through that CLI on that host clears the mark, with no restart
#### Scenario: No command can fix it
- **WHEN** the CLI is not installed
- **THEN** the mark names it and is not pressable
#### Scenario: The assistant's MCP server needs a login
- **WHEN** a failed read finds an MCP server down
- **THEN** the mark opens `claude` as the Session tab's login window, `say` types `/mcp`; a minutely read finding all connected clears it
#### Scenario: The release feed cannot be read
- **WHEN** a release check cannot read the feed
- **THEN** the head mark names its driver and host, and the next good read clears it

### Requirement: A foreign request is refused
The refused-request door SHALL refuse a foreign Host or Origin unless the host is the `publicUrl`'s or in `auth.allowedHosts`.
It SHALL refuse a write without Origin but to `/mcp` and `/api/agent/`; the firewall SHALL reject a respelled path.
#### Scenario: Behind an ingress, or a stranger posts
- **WHEN** a request's Host is `server.publicUrl`'s or is in `auth.allowedHosts`
- **THEN** the refused-request door lets it through
- **WHEN** a foreign page, a rebound name, a session's `curl` or a respelled `/api;x/…` posts a verb
- **THEN** refused: 403, or 400 where the firewall rejects the spelling; `ToolGate` stops the `curl` first

### Requirement: A login jagt opens shows its code whole
A login jagt opens SHALL open no browser: the board shows the login's one-time code and device URL whole.
Where the driver's CLI offers no device flow, its own login prompt shows on the Session tab, and `say` answers it.
#### Scenario: A login with or without a device flow
- **WHEN** a code host's CLI offers a device flow and its login starts
- **THEN** the board shows the one-time code and the device URL whole, and jagt opens no browser
- **WHEN** a code host's CLI offers no device flow
- **THEN** its own login prompt shows on the Session tab, and `say` answers it

### Requirement: Every enabled model role's log opens in the board's pane
Each model role that runs SHALL have one chip in the head, named by its id: the Master, acceptance, triage and any added later.
Each has one feed, an in-memory logback appender `ROLE_<id>` on its module's classes, read at `/api/roles/<id>/log`, announced by `role-log`.
A disabled role, or one a human holds, has no chip. jagt opens no tmux window for a role.
#### Scenario: A role's log
- **WHEN** the human presses the Master's chip
- **THEN** its log stands in the pane, newest entry on top, and follows new entries
- **WHEN** the human presses the chip of the open log
- **THEN** the pane hides it and the Master keeps working
#### Scenario: A role that does not run
- **WHEN** a role is disabled, or a human holds it
- **THEN** the head shows no chip for it

### Requirement: Every log view lists newest first
The Master's and every role's log, the session log and the `tail` report SHALL render through one `LogLines` component.
One block per entry, newest on top, a 6px gap; time (`HH:MM:SS` UTC) and INFO level `--muted`, WARN and ERROR `--danger`, message `--text`.
The legend has a row for the muted time and one for a danger entry. The session tab's `.screen` stays in terminal order.
#### Scenario: A log
- **WHEN** the human opens the Master's log holding a refusal, a move and a note
- **THEN** the newest entry is on top, a visible gap separates entries, the refusal is `--danger` and each time is muted
- **WHEN** entries arrive while the human has scrolled down
- **THEN** the entries being read stay in view
