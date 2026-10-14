## Why

Every role of the brief read every round, so a round touching no file a role judges still paid its heavy read.

## What Changes

- The brief's role table takes an `applies` column of path globs; a round whose diff touches none skips that role.
- Its columns past the question are found by their header's name.

## Capabilities

### Modified Capabilities

- `master`: a role whose globs the diff misses is not read.
