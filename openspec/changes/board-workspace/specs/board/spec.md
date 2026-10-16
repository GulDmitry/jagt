## MODIFIED Requirements

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

### Requirement: Facts sit on the thing they are about
Rows SHALL keep their order inside their group: the tasks waiting on the human first, then each project in the
order it first appears; only `order: alias` lets a new task take a retired one's place. The filter box (`/`)
matches alias, ticket number or title; `Esc` clears. The task number opens the ticket, the `MR` chip the request;
across repositories each project's name opens its own. The chip shows the request's own age, green with ✓ once
approved. The Deploy button is green while the work is live. A `⚐` beside the title flags the task, in this
browser alone.

#### Scenario: Taking the colour off
- **WHEN** a live task is reverted
- **THEN** the Deploy button loses its green

#### Scenario: A task turns to the human
- **WHEN** a task's checks fail while another task's agent works
- **THEN** its row moves into the band on top, and the other row keeps its place

## ADDED Requirements

### Requirement: A task is one row
Each task SHALL be one row of at most two lines: alias, title, badge and the one verb the server marks `onRow`; then
the status with its age and the checks dot. A row whose poll stopped rings its status; in the band on top a row
names its project where projects are several.

#### Scenario: A poll stops on a closed task
- **WHEN** a1's poll stops while a2 is open
- **THEN** a1's row rings its status

#### Scenario: Focus from the list
- **WHEN** the human presses the row verb of a task that is not open
- **THEN** Focus runs on that task and the pane keeps the task it had

### Requirement: One task is open beside the list
The picked row SHALL open in a pane as the task's card, whole, and its status history; the pane stays in view while
the list scrolls. With nothing picked, the first task waiting on the human is open, else the first row. Under 1200 px
the pane replaces the list, and `Esc` or its back button returns.

#### Scenario: Picking a row
- **WHEN** the human picks a2's row
- **THEN** a2's card stands in the pane and its row reads selected

#### Scenario: A link names a task
- **WHEN** a banner opens the board on `?task=a2` and the human clears the filter
- **THEN** every row is back and a2 stays open

### Requirement: The tab says what waits
The page's title SHALL read `(n) jagt` while n tasks wait on the human, and `jagt` otherwise. Its icon SHALL wear a
red dot while a task shows a problem, a job's last run failed or `act` stopped, else an amber one while a task waits.

#### Scenario: One task waits
- **WHEN** one task's checks fail
- **THEN** the title reads `(1) jagt`

### Requirement: A change while closed is bold
A row whose status moved since the human last had it open SHALL show its title bold until it is opened. What was
seen is kept in this browser alone; a task met for the first time counts as seen.

#### Scenario: A round comes back
- **WHEN** a2's status moves while a1 is open
- **THEN** a2's title is bold, and picking a2 clears it

### Requirement: Keys walk the list
`j` and `k` SHALL move the open task down and up the list, `n` SHALL open the next task waiting on the human, and
`f` SHALL run Focus on the open task. A key does nothing while the human types into a field or a dialog is open.

#### Scenario: The next task waiting
- **WHEN** a1 and a3 wait on the human, a1 is open and the human presses `n`
- **THEN** a3 is open

### Requirement: A page follows a restart
The push stream's first event SHALL name the run that serves it. A page that reconnects to a different run SHALL
load itself again, so it never keeps the code of a board that is gone.

#### Scenario: jagt restarts under an open tab
- **WHEN** jagt restarts on a new jar while a tab stays open
- **THEN** the tab reconnects, sees a new run and loads the new board

### Requirement: The header says only what needs a look
The header SHALL hold the count of tasks waiting on the human, a failed job, the Master while it acts, review polling
while it is off, the filter, one Reports menu and New task. A failed job's chip opens the jobs.

#### Scenario: A job fails
- **WHEN** a job's last run failed
- **THEN** a chip says how many of how many failed, and pressing it opens the jobs with that row marked

### Requirement: Every job can run now
The jobs SHALL show as rows grouped by whom each serves, with the server's words, the last run, the next and its
error; each row's Run now sends `run <job>`.

#### Scenario: Running a job by hand
- **WHEN** the human presses Run now on idecleanup
- **THEN** it runs within a second, and the toast says so

### Requirement: A project is added from the board
Add a project SHALL read a repository path as it is typed — a git repository, its origin, the branch tasks are cut
from, a key no project holds — fill the fields the human has not typed, and write one entry under `projects:` in
`jagt.yml`, leaving every other line as it was. A path that is not ready is refused and nothing is written.

#### Scenario: A folder that is no repository
- **WHEN** the human types the path of a plain folder and adds it
- **THEN** the reading says it is not a git repository, the add is refused, and `jagt.yml` is unchanged

### Requirement: The open task has three tabs
The open task SHALL show its status history, its session's screen read without colour codes with a line to tell
the session, and its drafted review replies or that none are drafted.

#### Scenario: Reading the session
- **WHEN** the human opens the Session tab
- **THEN** the session's screen as it reads now stands there, and a line typed under it reaches that session

### Requirement: A verb carries its key
A verb SHALL carry the letter that runs it on the open task, drawn on its button, from the server; a verb that cannot
be taken back carries none, and `Done` stands apart at the end of its row.

#### Scenario: Focus by key
- **WHEN** the human presses `f` with a1 open
- **THEN** Focus runs on a1
