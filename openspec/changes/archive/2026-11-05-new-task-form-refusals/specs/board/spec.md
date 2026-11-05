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
#### Scenario: A project is hovered in the launch row
- **WHEN** the human hovers a project's name or box
- **THEN** its hover shows the project's whole `jagt.yml` entry and the address of its repository's remote
#### Scenario: Start with no project
- **WHEN** the human presses Start with no box ticked and `plan first` unticked
- **THEN** the form says "Choose a project, or start with plan first" and sends nothing
- **AND** it says the server's other refusals itself: spaces in the ticket, no ticket and no notes, notes alone and no project of several
- **WHEN** `plan first` is ticked
- **THEN** Start sends the task with no project
#### Scenario: Resuming a request
- **WHEN** the human resumes a review request
- **THEN** `resume <url>` after ⌘K is the one control
