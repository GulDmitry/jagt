## MODIFIED Requirements

### Requirement: The open task has its six tabs
The open task SHALL have the tabs Overview, Drafted review, Artifacts, Code, Diff and Session, in that order. Overview shows its status
history, newest first. Session is the session's live terminal (`session-stream`), and the Keys row. Drafted review shows its drafted replies, or
that none are drafted. Artifacts lists the task's documents (`Artifact`) in its worktree now, in declared order, the one picked rendered. Code
reads its worktree, Diff the task's change (`board-diff`).
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
