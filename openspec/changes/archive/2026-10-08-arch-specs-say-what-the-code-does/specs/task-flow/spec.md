## MODIFIED Requirements

### Requirement: Nothing below flow decides a status
A capability SHALL report `OK` / `RELAYED` / `CONFLICT` / `PARTIAL` / `GONE` plus the sentence and the stamp, so
work serves several statuses. Its sentence MAY name the status; nothing parses it. `withStatus` MUST appear only in
`flow/` and its implementing record.

#### Scenario: Grepping for withStatus
- **WHEN** the sources are grepped
- **THEN** only `flow/` and its record match

### Requirement: Starting a task
`do` SHALL cut a branch from the project's base into its own worktree, an agent in it. At most 24 tasks SHALL be
open (`TaskProvisioning.MAX_TASKS`).

#### Scenario: New ticket
- **WHEN** you run `do ABC-42`, or `do ABC-42 <project>`
- **THEN** branch `ABC-42` is cut from the project's base, in its own worktree, an agent in it

#### Scenario: Work with no ticket
- **WHEN** you run `do <project> <what to do…>`
- **THEN** your words are the instructions and name the branch

#### Scenario: Plan before code
- **WHEN** you pick `plan first`
- **THEN** the card reads `plan waiting`; your next instruction to the session approves it

#### Scenario: Starting from another branch
- **WHEN** you run `do ABC-42 from <branch>`
- **THEN** it is cut from that branch and the request targets it; deploy still goes to `deployBranch`

#### Scenario: A twenty-fifth task
- **WHEN** 24 tasks are open and you start another
- **THEN** it is refused before anything is cut
