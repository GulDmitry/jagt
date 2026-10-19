## ADDED Requirements

### Requirement: The editor is a launcher
jagt SHALL open a worktree and a diff only through `editorCommand` and the optional `editorDiffCommand`, naming no editor.
IDE service folders present in the base repository SHALL be copied into a fresh worktree.
A file the worktree already holds SHALL stay as it is.

#### Scenario: IntelliJ run configurations
- **WHEN** the base repository holds `.idea/runConfigurations`
- **THEN** a fresh worktree holds the same run configurations

#### Scenario: VS Code launch file
- **WHEN** the base repository holds `.vscode/launch.json`
- **THEN** a fresh worktree holds the same file

#### Scenario: Neither IDE
- **WHEN** the base repository holds no IDE service folder
- **THEN** nothing is copied

#### Scenario: No diff command
- **WHEN** `editorDiffCommand` is unset and the human presses diff
- **THEN** it is refused, naming the key and an example; jagt still starts
