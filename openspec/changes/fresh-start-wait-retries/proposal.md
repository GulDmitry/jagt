## Why

A read that failed during a fresh start's wait ended the wait, so the round never started until the next relay.

## What Changes

- The wait is a job tick of two seconds: one read per tick, a failed read keeps the wait and is read again.
- The human must be seen gone on two consecutive reads.

## Capabilities

### Modified Capabilities

- `cold-prompts`: the fresh start's wait counts two consecutive reads and survives a failed read.
