## MODIFIED Requirements

### Requirement: The editor is a launcher
jagt SHALL open a worktree and a diff only through `editorCommand` and the optional `editorDiffCommand`, naming no editor.
`orchestrator.editor` SHALL be `cli` (default) or `none`; `none` opens nothing. `editorCommand` has no default: unset, `code` opens
the board's viewer alone and startup raises no problem; set, `code` also launches it.
IDE service folders present in the base repository SHALL be copied into a fresh worktree. A file the worktree already holds SHALL stay as it is.

#### Scenario: IDE service folders, or no diff command
- **WHEN** the base repository holds `.idea/runConfigurations` or `.vscode/launch.json`
- **THEN** a fresh worktree holds the same run configurations or file
- **WHEN** the base repository holds no IDE service folder
- **THEN** nothing is copied
- **WHEN** `editorDiffCommand` is unset and the human presses diff
- **THEN** it is refused, naming the key and an example; jagt still starts

#### Scenario: open with no editor
- **WHEN** `orchestrator.editor` is `none` and the human presses open or diff
- **THEN** it is refused with a sentence naming `editor` as the key that enables it; jagt still starts

#### Scenario: no editorCommand
- **WHEN** `editorCommand` is unset and the human presses code
- **THEN** the Code tab opens, no editor launches and startup raises no problem
- **WHEN** `editorCommand` is set and the human presses code
- **THEN** the Code tab opens and the editor launches too
