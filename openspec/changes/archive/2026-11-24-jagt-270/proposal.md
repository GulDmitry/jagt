## Why

People, review bots and jagt's code reviewer write on one request, and the Master may answer them. The rules sat in four specs
with gaps: on 2026-11-24 the reviewer posted under the human's account on a task's own request after the Master shipped,
though the finding belonged to the session before the ship. Every actor, moment and configuration needs one written outcome.

## What changes

- New capability `review-actors`: the matrix of actors x moment x keys, one scenario per cell (C1-C4, P1-P5, B1-B2, H1-H2, S1).
  It takes over "Threads are resolved at ship", the drafted-replies requirement and the answered-by-hand scenarios from `review`.
- `code-review`: the reviewer never posts on a jagt task's own request (findings reach the session before ship, a failed
  pre-ship read's findings are relayed); bots are answered on third-party requests only; reply ratings are counted.
- `master`: drafts a ship posts are the matrix's cells.
- `unattended-work`: the Master in `act` may ship replies (P3), so "nothing posted without a human ship" goes.
- `review`: links to the matrix.

## Impact

Specs only; code follows in later steps of the task, each cell pinned by one parameterized test.
Departures of today's code from the matrix are fixed in those steps, not here.
