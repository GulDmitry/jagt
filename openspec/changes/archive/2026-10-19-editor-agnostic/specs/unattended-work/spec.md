## MODIFIED Requirements

### Requirement: Jobs that keep the machine tidy
Each of these jobs SHALL do its one thing unasked:
- `jarwatch`: each minute, tells you the jar this process runs from was rebuilt underneath it.
- `orphanscan`: at startup, deletes jagt's own worktree residue and warns about leftovers holding work or secrets.

#### Scenario: A husk of a worktree
- **WHEN** a worktree directory jagt cut has lost its checkout and holds only the editor's files
- **THEN** `orphanscan` deletes it at startup
