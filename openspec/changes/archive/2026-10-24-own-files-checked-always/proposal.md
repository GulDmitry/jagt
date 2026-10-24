## Why

A stand run started jagt with `startup-checks: false` and no case file in its root: the case check was skipped, the
book was empty, and every step became a miss retried every two seconds.

## What Changes

- `startup-checks: false` skips only the checks about the machine; a check of jagt's own files (the cases) runs always.

## Capabilities

### New Capabilities

### Modified Capabilities

- `startup`: switching the checks off keeps jagt's own files checked.
