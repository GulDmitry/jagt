## Why

A request once found merged stayed merged after the base was reset or the merge reverted, so `ship` stayed refused and auto-review stopped reading it.

## What Changes

- A request git found merged and the host never said merged is read again every 5 minutes; it is not merged once git no longer finds its tip in the target.
- A request the host said merged stays merged; a fetch that fails is no answer and changes nothing.

## Capabilities

### Modified Capabilities

- `git`: the Ship requirement states where a merge is decided and re-read; its scenario gains the reset base.
