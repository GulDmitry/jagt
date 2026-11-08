## Why

A restart empties jagt's copy of the ticket, and a ticket read can come back after a hand-back. The round's inputs then
differed from the verdict's although nothing a human changed: every role read the round again.

## What Changes

- The inputs digest holds the ticket's digest apart from the other texts.
- A ticket with no read standing is no change; a ticket read where the verdict had none, or read differently, is.
- The panel's log line names why it read (`trigger`) and over which round (`fingerprint`).

## Capabilities

### Modified Capabilities

- `master`: the kept verdict ignores an unread ticket.
