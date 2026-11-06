## MODIFIED Requirements

### Requirement: Keys walk the list
`j` and `k` SHALL move the open task down and up the list. `n` SHALL open the next task waiting on the human. `f`
SHALL run Focus on the open task. A key does nothing while the human types into a field or a dialog is open.
A shortcut's label SHALL name the modifier of the human's platform: `⌘K` on Apple, `Ctrl+K` elsewhere.

#### Scenario: The next task waiting
- **WHEN** a1 and a3 wait on the human, a1 is open and the human presses `n`
- **THEN** a3 is open

#### Scenario: A shortcut on Linux
- **WHEN** the board opens in a browser on Linux
- **THEN** the palette's button reads `Ctrl+K`
