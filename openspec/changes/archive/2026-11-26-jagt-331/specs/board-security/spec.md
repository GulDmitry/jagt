## ADDED Requirements

### Requirement: The policy hashes the import map the board serves
The `script-src` hashes SHALL be those of the import maps of the folder the board is served from: `orchestrator.board.dir` when set, else the jar's.
#### Scenario: A board folder with its own import map
- **WHEN** `board.dir` names a folder whose import map differs from the jar's
- **THEN** the policy carries the hash of the folder's import map
