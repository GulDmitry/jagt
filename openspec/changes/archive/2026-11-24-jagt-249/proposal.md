## Why

A task's session stopped mid-work on an npm E401: the project's registry token had expired. In the autonomous cycle a session never starts where a known credential is dead.

## What Changes

- A project may declare `credentialChecks`: read-only commands, each with a name and what renews the credential.
- jagt runs them at start, daily, and before a session of the project starts; a failing one marks the board's head and holds the session.
- jagt reads each command's exit code alone, never a credential's value.

## Capabilities

### New Capabilities

### Modified Capabilities

- `attention`: a failing credential check holds its project's session start and says so on the card.
