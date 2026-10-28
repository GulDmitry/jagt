## Why

The same failing job on the same commit was relayed again because the brief quoting the failure read a few characters differently (seen 2026-10-28).

## What Changes

- The digest a round is compared by covers the failing job, the pushed branch tip from git and the threads' newest notes, not the brief.

## Capabilities

### Modified Capabilities

- `review`: the unchanged-round requirement compares facts.
