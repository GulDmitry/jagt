## Why

A review round arriving hours after the hand-back was typed into the old conversation, rewriting its whole cache: a
headless run's log in the worktree read as the session's activity, and a nudge held behind a draft never re-read the
idle time.

## What Changes

- A code-review round on a request starts a fresh session from `task_notes.md`, whatever the idle time.
- Only the interactive session's own log measures its idle time.
- A nudge held behind a draft starts a fresh session once the conversation idled past the cache lifetime.

## Capabilities

### Modified Capabilities

- `runtime`: a review round starts fresh; idle time is the session's own.
