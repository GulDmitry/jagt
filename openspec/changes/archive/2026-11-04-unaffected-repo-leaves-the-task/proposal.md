## Why

A repository the approved plan marks `[unaffected: …]` stays on the task, and its label stays on the card.

## What Changes

- On plan approval every repository the plan tags unaffected leaves the task: checkout removed, branch kept, session told.
- A repository whose task branch holds own commits stays, and an INFO line says why.
- A task never loses its last repository.

## Capabilities

### Modified Capabilities

- `acceptance-probes`: a multi-repository plan names every repository.
