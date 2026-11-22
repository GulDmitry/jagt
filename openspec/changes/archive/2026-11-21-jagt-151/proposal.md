## Why

A numeric key set to zero, a negative or past its ceiling was read as its default or clamped, with no word.

## What Changes

- A numeric key outside its range refuses the start, naming the key, the value and the range.
- An unset key keeps its default.

## Impact

- `openspec/specs/project-setup/spec.md`
