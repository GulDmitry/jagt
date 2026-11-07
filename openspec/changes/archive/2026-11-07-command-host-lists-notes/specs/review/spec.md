## ADDED Requirements

### Requirement: The command driver reads note ids from its notesCommand
The `command` code-host driver SHALL read a request's unresolved note ids from the project's `notesCommand`, run with `{request}`
replaced, one id per line; unset, it gives none.
#### Scenario: The command host lists the notes
- **WHEN** `codeHost: command` has a `notesCommand` printing two ids and the next poll prints the same
- **THEN** the round is the same and is relayed once
