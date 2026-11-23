## Why

A third-party request is read by no one in jagt, and a session's code is read once. A role on by default reviews both,
answers review bots and learns from likes and dislikes ([decision 0231](../../../docs/decisions/0231-a-code-reviewer-role-posts-notes-on-requests.md)).

## What Changes

- A `code-reviewer` role: a job with no region, posting notes as the seventh code-host write; `AGENTS.md` names it in place.
- The Master's brief gains an `unless` column; a note opening with the AI line awaits an answer whoever posted it.
- `unattended-work` names a note beside the re-run as a host write.

## Capabilities

### New Capabilities

- `code-review`: reading a request's change, posting notes once, answering bots, the ledger and the statistics.

### Modified Capabilities

- `unattended-work`: a host write is a re-run or a note.
- `review`: a note opening with the AI line awaits an answer whoever posted it.
- `master`: a brief row is skipped while the code reviewer is on and the project has a code host.
