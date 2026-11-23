## Why

The code reviewer ([decision 0231](../../../docs/decisions/0231-a-code-reviewer-role-posts-notes-on-requests.md)) landed with gaps:
it judged requests already over, posted every finding on the request instead of its line, could not rate a top-level note,
let a third-party read cost without bound, took resolutions from the host alone and left the Master's brief reading what it reads.

## What Changes

- A request's state (open, merged, closed) comes with its change; an over request drops its judgement and its row ends.
- A finding naming a file and a line is posted on that line, on the request where the host refuses the line.
- A top-level note's likes and dislikes are read from the host's own comment endpoint.
- A third-party read is charged to the request's ledger row and stops at `assistant.max-cost-usd`.
- A jagt task's request takes its resolutions from `review_replies.md`, matched by the note's link.
- The command driver's notes carry body and author, or the read fails saying they are cannot-say.
- The Master's QA row yields to the code reviewer; its skipped line names the reason that applied.

## Capabilities

### Modified Capabilities

- `code-review`: state, inline notes, top-level reactions, the cost gate and the resolutions.
- `master`: a skipped line names why the row was skipped.
