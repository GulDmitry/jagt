## Why

A pipeline died on a lost runner and the session was told to fix a build that was not broken; it asked a human about infrastructure.

## What Changes

- A red the host says is infrastructure alone is re-run by jagt after each wait of `infraWait.backoff` (default 5m, 15m, 45m), not relayed.
- The reasons are read through the host's own CLI; the sweep reports only the newest pipeline's address.
- After the last wait, or when the host cannot be asked, the task stops as `INFRA`.
- `infraWait.backoff: []` turns it off.

## Capabilities

### New Capabilities

- `infra-wait`: infrastructure failures are waited out; a job re-runs the pipeline.

### Modified Capabilities

- `review`: a red round goes back to the session unless the host calls it infrastructure.
- `unattended-work`: the re-run counts toward the hour's ceiling.
