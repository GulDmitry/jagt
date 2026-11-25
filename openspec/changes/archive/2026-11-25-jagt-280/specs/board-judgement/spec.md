## ADDED Requirements

### Requirement: An unreadable checkout pointer is not an orphan
A worktree folder whose `.git` pointer cannot be read SHALL NOT be listed as an orphan. Its state is unknown, so the scan logs it and leaves it.
#### Scenario: A pointer that cannot be read
- **WHEN** the `.git` pointer of the live task's `ABC-41-deploy` folder cannot be read
- **THEN** `worktrees` does not list it
