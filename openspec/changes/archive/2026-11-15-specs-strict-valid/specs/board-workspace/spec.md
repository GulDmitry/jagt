## REMOVED Requirements

### Requirement: The open task has five tabs
**Reason**: Split into shorter requirements, every fact kept.
**Migration**: None.

## ADDED Requirements

### Requirement: The open task has its five tabs
The open task SHALL have the tabs Overview, Drafted review, Artifacts, Diff and Session, in that order. Overview shows its status history,
newest first. Session shows its session's screen read without colour codes and the Keys row. Drafted review shows its drafted review replies, or that
none are drafted. Artifacts lists the documents of the task (`Artifact`) present in its worktree now, in the order `Artifact` declares
them, the one picked rendered. Diff shows the task's change (`board-diff`).
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
