## Why

Mutants cost minutes; the same diff run again repeated the work and printed the same survivors.

## What Changes

- `pitestDiff` keys its result on the diff: Gradle's up-to-date check over the changed-lines file, the target classes and the reports.
- The same diff prints the stored survivors without mutating; any change to the diff mutates again.

## Capabilities

### New Capabilities

### Modified Capabilities

- `engineering`: mutants run once per diff.
