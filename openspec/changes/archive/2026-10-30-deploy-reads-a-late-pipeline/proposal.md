## Why

A deploy that stood because the host listed no pipeline within 10 minutes was never read again, so a pipeline the
host started later, red or green, went unseen.

## What Changes

- A target that settled with no pipeline listed is recorded as bare on the deployment record.
- `DeployCheckJob` keeps reading a bare target's tip while the task is `DEPLOYED`: red on the code hands it back as
  any red does, a pipeline read green ends the reading.

## Capabilities

### Modified Capabilities

- `infra-wait`: the deploy pipeline requirement and its no-pipeline scenario.
