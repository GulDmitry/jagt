## Why

A fresh review of JAGT-270 found the ship's brief told the session to post every drafted reply whatever its author kind,
so a reviewer's block, or a person's where the Master may not reply unasked, could be posted. A failed read of a request's
notes also made every author a person.

## What changes

- `review-actors`: a ship posts per kind (reviewer and unknown never, person by the RPU cell, bot by the filter); kind `unknown`
  where the host's notes could not be read; reply ratings carry no reviewer brief digest; the signals read only requests
  a task holds or the ledger names; the cells are pinned by named tests, not one parameterized test.

## Impact

`ShipService.repliesStep`, `ThreadAuthors`, `ReplyBlocks`, `ReviewDrafts`, `SignalRecorder`, `ReviewSignalsJob`.
