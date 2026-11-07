## Why

A session linked a review request that lived in another repository, and the task recorded it as the round.

## What Changes

- A review-request link whose path is not the linked repository's own remote is dropped, never recorded.
- A ship whose only link is dropped records no round and leaves the status; a ship over several repositories keeps its good links.
- The board's head says each dropped link, ending on that repository's next good link or when the task closes.
- The reporting session is told once which remote was expected.
- A foreign request already in `state.json` is cleared on the first read.

## Capabilities

### Modified Capabilities

- `repo-attach`: a link must be under the repository's own remote.
