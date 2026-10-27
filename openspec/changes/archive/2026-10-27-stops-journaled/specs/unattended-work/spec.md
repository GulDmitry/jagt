## MODIFIED Requirements

### Requirement: jagt stops a task it suspects it is looping
`TaskStops` SHALL stop a task, its status kept, on any trigger since the last `lift`:
`loopBreaker.stalledRounds` (3) relayed rounds running that each brought no new note and left the same job failing;
a Master ship past `loopBreaker.ships` (4) within `autoReview.windowHours` that answered a round bringing no new note;
a failed verification or probe, a not-ready plan or round, repeats too, or a review round, sent back past `loopBreaker.rounds` (3) running in 24h.
Every stop SHALL append a line `{at, task, alias, trigger, status, evidence}` to `stops.jsonl` beside `state.json`
(rolled at 1 MB), which `lift` leaves.
