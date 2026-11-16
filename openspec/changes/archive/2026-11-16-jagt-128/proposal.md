## Why
A password board could be guessed at without limit, and a login left no line: nothing said who tried or from where.

## What Changes
- A peer address failing the password `orchestrator.auth.backoff.failures` times is refused with 429 for `orchestrator.auth.backoff.duration`.
- Every login, password or oidc, success or failure, is one structured log line with the peer and the mode.
