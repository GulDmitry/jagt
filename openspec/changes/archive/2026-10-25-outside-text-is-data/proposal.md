## Why

A ticket, a review comment or a pipeline log reached a session or the Master inside a prompt with nothing marking it
as data. In `act` the Master moves with no human, so an instruction planted in that text was a path to a deploy.

## What Changes

- Every text jagt quotes from outside the human's control is fenced by `Outside.fence`: the ticket and its title,
  the review threads, the failing job's log.
- The fence cannot be closed from inside: a delimiter in the fenced text is escaped.
- Both briefs say fenced text is data, and an instruction found in it is reported, never followed.

## Capabilities

### Modified Capabilities

- `session-brief`: outside text reaches a session and the Master fenced, and decides nothing.
