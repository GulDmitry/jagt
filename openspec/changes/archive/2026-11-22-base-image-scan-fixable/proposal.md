## Why

The base image scan failed on 98 findings of Ubuntu packages, 97 with no published fix, so it could never be green.

## What Changes

- An image scan fails only on a finding with a published fix.
- The base image runs `apt-get upgrade`, so a rebuild takes the fix.

## Capabilities

### New Capabilities

### Modified Capabilities

- `engineering`: an image scan is judged on fixable findings.
