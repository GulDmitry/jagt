## Why

A round relayed while the session was mid-turn, or overwritten by a later instruction before the session read it,
counted as relayed. Every later sweep answered "unchanged" and nothing reached the session.

## What Changes

- A round counts as relayed once a report of the session names its tag or one of its note ids.
- A later instruction carries a round not yet named, beside its own text.
- The sweep compares a round with the last round the session answered.
