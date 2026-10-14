## Why

A not-ready round went back to the session however often the same finding came back. Replayed on the
`master-decisions.md` of 13 finished tasks: 38 findings in 14 not-ready rounds of 5 tasks. Matched on file and
pattern-name words, Jaccard ≥ 0.5: 7 repeats, all true by hand, 3 reworded patterns missed; 0.3 to 0.7 match the same.
On file alone: 15, half false. On the issue's words: 2. One round held only repeats, never two running.

## What Changes

- A finding matching an earlier round's on the same file goes back marked `[repeat]`.
- Two not-ready rounds running holding only repeats make the round the human's question; in `act` the Master does
  not answer it.

## Capabilities

### Modified Capabilities

- `master`: rounds of repeats reach the human.
