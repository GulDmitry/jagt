## Why

A prompt typed before a fresh session's first hook landed was judged against the old session's idle stamp and could open a second fresh session.

## What Changes

- Starting a fresh session starts the task's session record over.

## Capabilities

### Modified Capabilities

- `cold-prompts`: the record starts over with the fresh session.
