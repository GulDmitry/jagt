# board-workspace Specification

## Purpose

The board's task list, the open task beside it, and the header: where a task stands and what the human presses.

## Requirements

### Requirement: Rows keep their place
Rows SHALL keep their order inside their group: the tasks waiting on the human first, then each project by name; only `order: alias` lets a
new task take a retired one's place. The filter box (`/`) matches alias, ticket number or title; `Esc` clears.

#### Scenario: A task turns to the human
- **WHEN** a task's checks fail while another task's agent works
- **THEN** its row moves into the band on top, and the other row keeps its place

#### Scenario: Projects by name
- **WHEN** `jagt.yml` lists `beta` before `alpha`
- **THEN** the task list and the launch boxes show `alpha` first; ticking both still sends `beta,alpha`

### Requirement: A task is one row
Each task SHALL be one row of at most two lines: alias, title, badge and the one verb the server marks `onRow`; then the status with its age
and the checks dot. A row whose poll stopped rings its status; in the band on top a row names its project where projects are several.

#### Scenario: A title too long for its row
- **WHEN** a1's title is wider than its row
- **THEN** the row ends it in `…`, and hovering the title shows it whole; a title that fits shows no tip

#### Scenario: A poll stops on a closed task
- **WHEN** a1's poll stops while a2 is open
- **THEN** a1's row rings its status

#### Scenario: A task is flagged
- **WHEN** a task is flagged
- **THEN** its row leads its group

#### Scenario: Focus from the list
- **WHEN** the human presses the row verb of a task that is not open
- **THEN** Focus runs on that task and the pane keeps the task it had

### Requirement: One task is open beside the list
The picked row SHALL open in a pane as the task's card, whole, and its status history; the pane stays in view while the list scrolls. With
nothing picked, the first task waiting on the human is open, else the first row. Under 1200 px the pane replaces the list, and `Esc` or its
back button returns.

#### Scenario: Picking a row
- **WHEN** the human picks a2's row
- **THEN** a2's card stands in the pane and its row reads selected

#### Scenario: A link names a task
- **WHEN** a banner opens the board on `?task=a2` and the human clears the filter
- **THEN** every row is back and a2 stays open

### Requirement: The tab says what waits
The page's title SHALL read `(n) jagt` while n tasks wait on the human, and `jagt` otherwise. Its icon SHALL wear a red dot while a task
shows a problem, a job's last run failed or `act` stopped. Else it wears an amber one while a task waits.

#### Scenario: One task waits
- **WHEN** one task's checks fail
- **THEN** the title reads `(1) jagt`

### Requirement: A change while closed is bold
A row whose status moved since the human last had it open SHALL show its title bold until it is opened. What was seen is kept in this
browser alone; a task met for the first time counts as seen.

#### Scenario: A round comes back
- **WHEN** a2's status moves while a1 is open
- **THEN** a2's title is bold, and picking a2 clears it

### Requirement: Keys walk the list
`j` and `k` SHALL move the open task down and up the list. `n` SHALL open the next task waiting on the human. `f` SHALL run Focus on the
open task. A key does nothing while the human types into a field or a dialog is open. A shortcut's label SHALL name the modifier of the
human's platform: `⌘K` on Apple, `Ctrl+K` elsewhere.

#### Scenario: The next task waiting
- **WHEN** a1 and a3 wait on the human, a1 is open and the human presses `n`
- **THEN** a3 is open

#### Scenario: A shortcut on Linux
- **WHEN** the board opens in a browser on Linux
- **THEN** the palette's button reads `Ctrl+K`

### Requirement: A page follows a restart
The push stream's first event SHALL name the run that serves it. A page that reconnects to a different run SHALL load itself again, so it
never keeps the code of a board that is gone.

#### Scenario: jagt restarts under an open tab
- **WHEN** jagt restarts on a new jar while a tab stays open
- **THEN** the tab reconnects, sees a new run and loads the new board

### Requirement: The header says only what needs a look
The header SHALL hold the count of tasks waiting on the human, a failed job and `act` stopped. It holds the Master while it acts, review
polling while it is off, the filter, one Reports menu and New task. A failed job's chip opens the jobs.

#### Scenario: A job fails
- **WHEN** a job's last run failed
- **THEN** a chip says how many of how many failed, and pressing it opens the jobs with that row marked

### Requirement: Every job can run now
The jobs SHALL show as rows grouped by whom each serves, with the server's words, the last run and its error; the next run sits in the row's
hover. Each row's Run now sends `run <job>`.

#### Scenario: Running a job by hand
- **WHEN** the human presses Run now on jarwatch
- **THEN** it runs within a second, and the toast says so

### Requirement: A verb carries its key
A verb SHALL carry the letter that runs it on the open task, drawn on its button, from the server; a verb that asks before it runs asks on
its key too, and `Done` carries none and stands apart at the end of its row.

#### Scenario: Focus by key
- **WHEN** the human presses `f` with a1 open
- **THEN** Focus runs on a1

### Requirement: The history names who asked
Each step of a task's status history SHALL name who asked for it (`task/turn/ActionOrigin`), the opening step what opened the task: the
board, the palette, a task session's MCP call, the root session's or the tracker. A step jagt took unasked names nobody. A step whose asker
gave a reason SHALL show it in the step's hover alone. The history holds 50 steps. Past that the oldest after the opening step drop; the
opening step stays. Time held per owner leaves out the gap after it.

#### Scenario: Opened twice, then the root moves one
- **WHEN** ABC-42 is opened on the board and ABC-43 by an `initialize_task` call
- **THEN** ABC-42's first step reads `board` and ABC-43's `mcp`
- **WHEN** the root session, holding the Master token in no task's worktree, moves ABC-42
- **THEN** that step reads `root`, and ABC-42's session does not take it as its own answer

#### Scenario: Why a step was taken
- **WHEN** ABC-42's session hands back with `why` "work done, tests green, ready for review"
- **THEN** hovering its REVIEW_PENDING step reads that sentence, and neither the card nor the step's row shows it

#### Scenario: A long task
- **WHEN** ABC-42 opened on the board takes a 60th step
- **THEN** its first step still reads `board`, the next 49 are the most recent, and no status is charged the time between

### Requirement: An item is placed in every project its labels name
An item whose labels or tracker project match configured projects SHALL be placed in every one of them (`ProjectRouting.projectFor`). The
router is asked only where no label matched and several projects are configured; it places the item in one project.

#### Scenario: Labels name two projects
- **WHEN** an item's labels match two configured projects
- **THEN** the task opens in both and the router is never asked

#### Scenario: No label matches
- **WHEN** no label matches and several projects are configured
- **THEN** the router reads the item and places it in one project, or in none

### Requirement: The open task has its six tabs
The open task SHALL have the tabs Overview, Drafted review, Artifacts, Code, Diff and Session, in that order, in one row.
The selected one is underlined, the card's verbs apart. Overview: status history, newest first. Session: the live terminal
(`session-stream`) and the Keys row. Drafted review: drafted replies, or that none are. Artifacts: the worktree's
documents (`Artifact`) now, in declared order, the picked one rendered. Code: the worktree. Diff: the change (`board-diff`).
#### Scenario: The views read as tabs
- **WHEN** ABC-42's pane is open
- **THEN** the six tabs stand in one row under the card, the selected one underlined
#### Scenario: Reading the session
- **WHEN** the human opens the Session tab
- **THEN** the session's terminal streams there, with the one message line under it and no second form
- **WHEN** no window holds the session
- **THEN** the tab says no window holds the session
#### Scenario: Reading the replies
- **WHEN** the human opens the Drafted review tab
- **THEN** it shows the drafted review replies, read from the command whose `part` is `round`, or that none are drafted
- **WHEN** `review_replies.md` is written after the human last opened the Drafted review tab of that task
- **THEN** the tab wears the your-move mark
- **AND** an offer whose `part` names a tab, replies today, is not drawn on the card
- **AND** opening the tab clears the mark, and a later write brings it back; the last opening is kept in this browser only

### Requirement: One message line is docked under every tab
One message line SHALL be docked at the bottom of the pane
under every tab, the only control that tells the task's session something; Enter sends the line to that task's session alone and
clears it, and `Run` sits beside `Send` where the session's CLI has a shell escape. While the session works and the Session tab is not
open, its last non-blank screen line shows above the message line. With no window holding the session the line is disabled and says why.
#### Scenario: The message line under any tab
- **WHEN** the human types a line under Overview, Artifacts or Diff and presses Enter
- **THEN** it reaches that task's session and the line clears
- **WHEN** the session works and another tab than Session is open
- **THEN** its last non-blank screen line shows above the message line
- **WHEN** no window holds the session
- **THEN** the message line is disabled and says why

### Requirement: The Code tab reads the worktree
The Code tab SHALL list the files of each repository of the open task's worktree, tracked or untracked and not ignored.
It SHALL show one file highlighted by its grammar and search the worktree's lines for a text. It reads on request, changes nothing, and its library
loads on first use, never with the page. A tree stops at 5000 files and a search at 200 lines, and the tab says so.
#### Scenario: Opening a task's code
- **WHEN** the human presses `code` on a task and no editor is configured
- **THEN** the Code tab opens with the worktree's files and one file highlighted
#### Scenario: A path leaves the worktree
- **WHEN** a request names `../x`, an absolute path, `.git` or a symlink whose target is outside the worktree
- **THEN** it is refused and nothing outside the worktree is read
#### Scenario: A file that is not text
- **WHEN** the human picks a binary file or one over 512 KB
- **THEN** the server answers its kind with no text, and the tab says it is not shown
#### Scenario: Searching
- **WHEN** the human searches `needle`
- **THEN** each line holding it, in any case, is listed with its file and number, and picking one opens the file there
