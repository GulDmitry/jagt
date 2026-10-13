## Why

Started together, every role of a panel wrote the round to the prompt cache, and the round sat in the same block as
the role's line, so no role could read another's write ([0038](../../../docs/decisions/0038-a-panels-first-role-writes-the-round.md)).

## What Changes

- The round joins the shared system prompt; the role's line alone is the prompt.
- The first role reads alone, the rest together once it answered.

## Capabilities

### Modified Capabilities

- `master`: the roles of one round read it from the first role's cache.
