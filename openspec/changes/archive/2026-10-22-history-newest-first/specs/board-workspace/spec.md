## MODIFIED Requirements

### Requirement: The open task has four tabs
The open task SHALL have the tabs Overview, Review round, Artifacts and Session, in that order. Overview shows its
status history, newest first. Session shows its session's screen read without colour codes, with a line to tell the
session. Review round shows its drafted review replies, or that none are drafted. Artifacts lists the documents of
the task (`Artifact`) present in its worktree now, in the order `Artifact` declares them, the one picked rendered.

#### Scenario: Reading the session
- **WHEN** the human opens the Session tab
- **THEN** the session's screen as it reads now stands there, and a line typed under it reaches that session

#### Scenario: A blank screen
- **WHEN** a window holds the session and its screen is blank
- **THEN** the tab shows it blank; it says no window holds the session only when none does

#### Scenario: Reading the replies
- **WHEN** the human opens the Review round tab
- **THEN** it shows the drafted review replies, read from the command whose `part` is `round`, or that none are drafted

#### Scenario: Reading the documents of a task
- **WHEN** the human opens the Artifacts tab of a task whose worktree holds `plan.md` and `task_visual.md`
- **THEN** both names are listed, the first is shown rendered, and picking the other shows it rendered

#### Scenario: The plan first, the reviewer's notes last
- **WHEN** the worktree holds `master-review.md`, `master-decisions.md`, `task_request.md`, `task_visual.md` and `plan.md`
- **THEN** they are listed `plan.md`, `task_visual.md`, `task_request.md`, `master-decisions.md`, `master-review.md`

#### Scenario: A Files tab replaces the worktree
- **WHEN** the human opens the Artifacts tab
- **THEN** it reads the disk on request and stores nothing
- **AND** `plan.md` or `task_notes.md` is read without opening the worktree in an editor or terminal

#### Scenario: A document that is not there
- **WHEN** the worktree holds no `task_visual.md`
- **THEN** the Artifacts tab does not list it

#### Scenario: Text that would run
- **WHEN** a document holds a raw `<script>` element
- **THEN** it is shown as text and nothing runs

#### Scenario: A diagram
- **WHEN** a document holds a mermaid fence
- **THEN** the Artifacts tab draws it as a diagram, loading the diagram library only then

#### Scenario: A name that is no document of a task
- **WHEN** a read names a file that is not in `Artifact`
- **THEN** it is refused as an unknown document, apart from an unknown task, an absent file and an unreadable one
