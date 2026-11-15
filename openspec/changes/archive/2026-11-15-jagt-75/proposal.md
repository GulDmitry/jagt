## Why

A second checker folder installs into the chart but runs no job: only acceptance's definition, files and settings are built.

## What Changes

- Every installed checker whose `role.yml` declares `jobs: true` runs its check, wait and decline jobs.
- A checker's `readSteps` are, in order, its check, its run again and its decline.
- Each checker keeps its own verdict records, `<id>-verdicts`.

## Impact

- Acceptance is unchanged: its steps, records and job ids stay.
