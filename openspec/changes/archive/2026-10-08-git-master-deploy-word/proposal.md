## Why

A Master `do` line was refused for any word equal to the deploy branch, so `fix the dev server` opened nothing.

## What Changes

- Only a branch after `from`, `into` or `onto` is read as the deploy branch; elsewhere the word is the task's.

## Capabilities

### Modified Capabilities

- `master`: the deploy-branch refusal reads the branch position, not every word.
