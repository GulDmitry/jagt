## Why

A tool-measured counted row never asked to be lowered, so `unlimited-runs` kept a ceiling of 8 above its count.

## What Changes

- A counted row measured by a tool ratchets like any other: below its number fails asking to lower it.
- `ratchet: false` exempts a row; the `contexts-*` rows carry it, as their fork count varies ([0239](../../../docs/decisions/0239-bad-practice-ratchet.md)).
- `unlimited-runs` and the other flagged tool rows fall to their measured count.
