## Why

A round carried red checks (a dependency not yet published) and a thread holding a reviewer's new answer. The session
took the "outside the code: change nothing" route for the checks, handed back without a reply block, and the thread
went unanswered; later sweeps read the round as unchanged.

## What Changes

- A session's hand-back is refused while `review_replies.md` was not written since a relay that carried threads.
- A standing question holds back only the checks of a round; a thread with a new newest note is still relayed, without the checks.
- The brief says the checks' state never decides whether a thread is answered.

## Capabilities

### Modified Capabilities

- `review`: threads and checks are two independent tracks of a round.
