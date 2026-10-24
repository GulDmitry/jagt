## Why

A case said which step jagt may send at which status, and nothing said what the session may answer: any report closed
the turn and landed (decision 0073, its second slice).

## What Changes

- Each case names the replies it takes: `replies: <statuses>; <kinds>`, the kinds plain, question, permission and
  no_changes. Startup refuses a case without one, or naming a status or kind that is none.
- A session's report closing an open turn with a reply no case of its step takes is refused: not applied, written to
  `misses.jsonl` with the step, what came and what the cases take, and the task stopped as an outbound miss is.

## Capabilities

### New Capabilities

### Modified Capabilities

- `cases`: a case names its replies, and a reply no case takes is a miss.
