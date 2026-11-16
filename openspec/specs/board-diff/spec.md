# board-diff Specification

## Purpose
Reading a task's change in the board, without opening an editor.

## Requirements

### Requirement: The Diff tab reads a task's change
The Diff tab SHALL show each repository of the open task under its base. It shows the unified diff from that
base's merge-base to the worktree as it stands. Committed or not counts, untracked files too, ignored ones not.
The base is the one the editor diff reads. It reads on request, stores nothing and opens no editor. An added line is bold
with a bar, a removed line struck through, a header muted: the marks of a plan's changed lines. The code verb stays.

#### Scenario: Reading the change without an editor
- **WHEN** the human opens the Diff tab of a task whose worktree changed `a.txt`, committed or not, and added `b.txt`
- **THEN** it shows the base and the unified diff of both files
- **AND** an added line is bold with a bar, a removed line struck through, a header muted

#### Scenario: Two repositories
- **WHEN** the task spans two projects with different base branches
- **THEN** each repository is shown under its project's name and its own base

#### Scenario: A change too large to show
- **WHEN** a repository's diff is longer than the read's cap
- **THEN** the tab shows the diff up to the cap and says it was cut, and the rest is not sent to the page

#### Scenario: Nothing to diff
- **WHEN** the task has no repository yet or its worktree changed nothing
- **THEN** the tab says there is nothing to diff, apart from a read that failed, which names its cause

### Requirement: The Code tab lists the change and opens one file beside its source
The Code tab SHALL offer the files the Diff tab reads as a list, each with its added and removed line counts.
It opens one as the file's text before and after the change side by side.
It takes the change from the same read as the Diff tab, loads its library on first use and stores nothing. A cut diff says so.
#### Scenario: A change touching many files
- **WHEN** the human opens Changes in the Code tab of a task that changed two files
- **THEN** both are listed with `+added −removed`
- **WHEN** the human picks one
- **THEN** its old text stands beside its current text, the changed lines marked
