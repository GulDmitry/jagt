## Why

A session read its ticket with the tracker's default fields, which omit custom fields; the ticket's 16 acceptance criteria
never reached `plan.md` and the plan was approved without them. A ticket's criteria may also span parts the task does not build.

## What Changes

- `plan.md` carries a `## Acceptance criteria` section of tagged items; jagt refuses a plan without it, or without a plan file.
- The session brief asks for the ticket read with every field; the Master's plan read flags a dropped or mis-tagged criterion.

## Capabilities

### New Capabilities

### Modified Capabilities

- `acceptance-probes`: a plan carries its tagged acceptance criteria.
