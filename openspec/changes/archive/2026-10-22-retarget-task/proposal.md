## Why

A task's base was fixed at creation. A session that rebased onto another task's branch on the human's word had no
way to tell jagt, so ship told it to open the request against the old base.

## What Changes

- `retarget_task` lets a session report a new base; jagt records it once origin has the branch and HEAD holds it.
- The briefing tells the session to rebase onto a base the human or the Master named, then call it.

## Capabilities

### New Capabilities

### Modified Capabilities

- `git`: ship targets the base a session reported with `retarget_task`.
