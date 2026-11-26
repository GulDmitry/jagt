## Why

The model check found `deploy` offered at `REVIEWED` or `APPROVED` while a re-run ran or after the read following a green failed.

## What Changes

- `deploy` at `REVIEWED` or `APPROVED` needs the stored checks green on the request's head, no read since failed.
- The Master and the approval deploy wait for that read before pressing once.
- Until then such a request is the host's turn: no badge, no Master holding it.

## Capabilities

### Modified Capabilities

- `deploy-on-approval`: deploy waits for green checks on the head.
- `attention`: a request waiting for a deploy before green checks is the host's.
