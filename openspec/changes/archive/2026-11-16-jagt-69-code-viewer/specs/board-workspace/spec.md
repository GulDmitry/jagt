## RENAMED Requirements
- FROM: `### Requirement: The open task has its five tabs`
- TO: `### Requirement: The open task has its six tabs`

## MODIFIED Requirements

### Requirement: The open task has its six tabs
The open task SHALL have the tabs Overview, Drafted review, Artifacts, Code, Diff and Session, in that order. Overview shows its status history,
newest first. Session shows its session's screen read without colour codes and the Keys row. Drafted review shows its drafted review replies, or that
none are drafted. Artifacts lists the documents of the task (`Artifact`) present in its worktree now, in the order `Artifact` declares
them, the one picked rendered. Code reads its worktree. Diff shows the task's change (`board-diff`).
#### Scenario: Reading the session
- **WHEN** the human opens the Session tab
- **THEN** the session's screen as it reads now stands there, with the one message line under it and no second form
- **WHEN** a window holds the session and its screen is blank
- **THEN** the tab shows it blank; it says no window holds the session only when none does
#### Scenario: Reading the replies
- **WHEN** the human opens the Drafted review tab
- **THEN** it shows the drafted review replies, read from the command whose `part` is `round`, or that none are drafted
- **WHEN** `review_replies.md` is written after the human last opened the Drafted review tab of that task
- **THEN** the tab wears the your-move mark
- **AND** an offer whose `part` names a tab, replies today, is not drawn on the card
- **AND** opening the tab clears the mark, and a later write brings it back; the last opening is kept in this browser only

## ADDED Requirements

### Requirement: The Code tab reads the worktree
The Code tab SHALL list the files of each repository of the open task's worktree (tracked, and untracked but not ignored), show one
file highlighted by its grammar and search the worktree's lines for a text. It reads on request, changes nothing, and its library
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
