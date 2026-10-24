## Why

Which step jagt may send a session at which status lived in code alone, at each place that relays one, so no human
could read it or extend it, and nothing refused a step sent where no rule meant it.

## What Changes

- `session-cases.md.dist` holds one case per step jagt sends, each its step, the statuses it is sent at and who sets
  it off; a human copy `session-cases.md` overrides it case by case, as the brief is overridden.
- Startup refuses a case naming a step the brief lacks, a brief step no case sends, and a case it cannot read.
- A step at a status no case names is not relayed: it is written to `misses.jsonl` beside `state.json` and logged.

## Capabilities

### New Capabilities

- `cases`: when a step may be sent to a session, as a file the human owns. Its own capability, not the brief's:
  the Master's and QA's cases join it later, and a case says when, never what.

### Modified Capabilities
