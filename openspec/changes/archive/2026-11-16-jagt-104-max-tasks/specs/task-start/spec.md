## MODIFIED Requirements

### Requirement: Starting a task
`do` SHALL cut a branch from the project's base into its own worktree, an agent in it; at most `maxTasks` open, 50 unless `jagt.yml` sets it.

#### Scenario: A task starts
- **WHEN** you run `do ABC-42`, or `do ABC-42 <project>`
- **THEN** branch `ABC-42` is cut from the project's base
- **WHEN** you run `do <project> <what to do…>`
- **THEN** your words are the instructions and name the branch
- **WHEN** you run `do ABC-42 from <branch>`
- **THEN** it is cut from that branch and the request targets it ([git](../git/spec.md))

#### Scenario: Plan before code
- **WHEN** you pick `plan first`
- **THEN** the card reads `plan waiting`; `approve-plan` starts the session on it

#### Scenario: A twenty-fifth task
- **WHEN** `maxTasks` tasks are open (50, or 10 where `jagt.yml` says 10) and you start another
- **THEN** it is refused before anything is cut, naming the cap
