## Why

`roles/session/cases.md.dist` holds a case `by: acceptance`, so a setup without `roles/acceptance` refused to start,
and every context test had to copy the acceptance role beside the session cases.

## What Changes

- A case whose `by` names a checker jagt ships but this setup has not installed is not loaded, and its step is not required.
- A `by` naming no shipped checker stays a startup refusal.

## Capabilities

### Modified Capabilities

- `cases`: startup checks the `by` of a case against the checkers jagt ships and the ones installed.
