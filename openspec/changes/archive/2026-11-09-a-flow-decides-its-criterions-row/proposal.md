## Why

A criterion the acceptance read calls unprovable (a login it lacks) stays unprovable, and the task asks a human, even when
the flow the read named for it passed: each flow's result was added as a row of its own, keyed by flow id, beside the read's.

## What Changes

- The read's `flows` answer is a list of `{id, criterion}`: per flow id, the criterion it checks, as written in a row's `criterion`.
- A flow naming a criterion decides that criterion's row: passed is met, failed not met, unprovable leaves the read's row
  with the flow's reason added. A question left behind a row now decided is dropped.
- A flow naming no criterion, or one no row has, keeps today's separate row.
- The accept prompt asks for the criterion of each flow.
- The acceptance eval runs the same merge over the named flows, so a flows case is scored on the merged rows.

## Impact

- Specs: `acceptance` (a new requirement), `acceptance-probes` (the flows requirement).
- `AcceptanceVerdict.Flows`, `CheckerAnswers`, `CheckerFlows`, `CheckerPrompts`, `roles/acceptance/role.yml.dist`,
  `AcceptanceVerdictEvalTest`.
