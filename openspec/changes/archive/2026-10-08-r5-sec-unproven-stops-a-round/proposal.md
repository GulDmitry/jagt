## Why

The shipped brief, this spec and `RoundReviewer.Finding` said only blocking and wrong stop a round, while the code
and the reviewer's prompt also stop on an unproven finding, sent as a `show:` request.

## What Changes

- An unproven finding stops a round as a `show:` request; an unproven premise stays advice.

## Capabilities

### Modified Capabilities

- `master`: the text says what the code does.
