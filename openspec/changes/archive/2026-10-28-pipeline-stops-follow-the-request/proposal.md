## Why

A stop on unreadable checks or an unchanged failure is a fact about one request's pipeline; it survived the request that
replaced it and refused the new request's sweeps.

## What Changes

- CHECKS and UNCHANGED_FAILURE stops are counted on the request, so a replacing request lifts them.

## Capabilities

### Modified Capabilities

- `unattended-work`: the stop-lift scenario names the triggers counted on a request.
