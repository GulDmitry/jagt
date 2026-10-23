## Why

A task's repositories are fixed when it opens. A session that finds it needs another configured project's repository
could only ask in prose, and nothing, the human included, could attach one.

## What Changes

- `update_agent_status` takes `needsProject` with `outcome=question`; jagt validates and records the request.
- `add-repo`, a per-task verb offered only on that question, cuts the worktree as `do` does and hands it to the session.
- `master.mine` gains `repo`; in `act` the Master presses `add-repo` and answers no prose.

## Capabilities

### New Capabilities

### Modified Capabilities

- `protocol`: the typed request for a repository.
- `git`: a running task gains a repository.
- `board`: the verb and where it stands.
- `master`: the `repo` right; the repository question is no longer only the human's.
