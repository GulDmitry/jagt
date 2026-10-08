## Why

A job declaring no id was dropped with a warning, so jagt started without work it was assembled to run.

## What Changes

- A job with a blank id refuses startup, naming its class.

## Capabilities

### Modified Capabilities

- `unattended-work`: a nameless job refuses startup rather than being dropped.
