## MODIFIED Requirements

### Requirement: A fresh start waits for the human
A fresh start SHALL NOT close a window holding input the human typed and has not sent.
Nor SHALL it while the turn their own prompt opened is still open. The instructions stay on disk and the fresh start waits, logging
`fresh start waits`. It proceeds when neither holds on two consecutive reads, two seconds apart, the window is gone or the task is removed.
A read that fails keeps the wait and is read again.

#### Scenario: A round arrives while the human types
- **WHEN** a new round arrives while the window holds the human's unsent input
- **THEN** the fresh start waits until it is sent and the turn ends

#### Scenario: A round arrives during the human's exchange
- **WHEN** a new round arrives while the turn their prompt opened is open
- **THEN** the fresh start waits until that turn ends

#### Scenario: The window dies while the fresh start waits
- **WHEN** the window is gone or the task removed during the wait
- **THEN** the wait ends and a fresh session starts, or nothing where the task is gone

#### Scenario: A read that fails keeps the wait
- **WHEN** the window cannot be read while the fresh start waits, and the human is gone on the reads after
- **THEN** the wait stands through the failure and the fresh session starts
