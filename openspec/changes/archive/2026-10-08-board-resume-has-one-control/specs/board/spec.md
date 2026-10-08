## MODIFIED Requirements

### Requirement: A mark says what it replaces
The colour legend SHALL be a section of the `help` report (`static/ui/legend.js`), never a control beside it; colours
are [design.md](../../../docs/rules/design.md)'s. A control the board lacks is the bug: the launch row's branch-strategy
picker and MCP schema both read `task/BranchStrategy`. A verb carrying all its inputs gets no form beside it. The
project key renders only where an install has several projects, or a task spans several.

#### Scenario: A mark is unclear
- **WHEN** the human opens `Help`
- **THEN** above the commands every mark stands beside its one board-wide meaning

#### Scenario: The launch row
- **WHEN** the human starts a task there
- **THEN** it offers ticket, project, base branch, `plan first`, notes, branch strategy, Start
- **AND** an untouched picker sends nothing

#### Scenario: Resuming a request
- **WHEN** the human resumes a review request
- **THEN** `resume <url>` in Ask is the one control
