## Why

The chart names QA as the only role that runs jobs and as the owner of the accept, again and decline steps.

## What Changes

- A role's `role.yml` declares `jobs:` and `readSteps:`; the chart answers both from the installed roles.
- Two installed roles naming one read step are refused.

## Capabilities

### Modified Capabilities

- `qa`: QA's read opens on the steps its `role.yml` names.
