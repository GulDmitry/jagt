## RENAMED Requirements

- FROM: `### Requirement: A cold prompt carries what the old session knew`
- TO: `### Requirement: A fresh start carries what the old session knew`

## MODIFIED Requirements

### Requirement: A fresh start carries what the old session knew
Beneath the instructions in `task_context.md`, a fresh start SHALL carry, gathered without a model: the last report or
open question, the commits and changed files since the base (40 lines each), and the old transcript's path.
It SHALL also carry the last human prompts and model texts of the old conversation. Those are newest kept within
16,000 characters, no tool input or output. A fresh start replacing a session with no transcript SHALL add nothing.

#### Scenario: A long conversation
- **WHEN** the old conversation holds more than 16,000 characters
- **THEN** the oldest are cut and no tool input or output appears

#### Scenario: A round replaces a session the human talked to
- **WHEN** a round's fresh start replaces a session whose transcript holds the human's last words
- **THEN** `task_context.md` holds the round's instructions first, then the human's last words

## ADDED Requirements

### Requirement: A fresh start waits for the human
A fresh start SHALL NOT close a window holding input the human typed and has not sent.
Nor SHALL it while the turn their own prompt opened is still open. The instructions stay on disk and the fresh start waits, logging
`fresh start waits`. It proceeds when neither holds for two seconds, the window is gone or the task is removed.

#### Scenario: A round arrives while the human types
- **WHEN** a new round arrives while the window holds the human's unsent input
- **THEN** the fresh start waits until it is sent and the turn ends

#### Scenario: A round arrives during the human's exchange
- **WHEN** a new round arrives while the turn their prompt opened is open
- **THEN** the fresh start waits until that turn ends

#### Scenario: The window dies while the fresh start waits
- **WHEN** the window is gone or the task removed during the wait
- **THEN** the wait ends and a fresh session starts, or nothing where the task is gone
