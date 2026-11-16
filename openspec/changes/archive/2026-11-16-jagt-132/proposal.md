## Why
A newer minor reads an older `/data`, but the server page named no backup or rollback and the state fixtures held one minor.

## What Changes
- `docs/server.md` says what `/data` holds, how to back it up and how to roll back.
- `StateServiceCompatibilityTest` reads a fixture of every shipped minor; the minor being built without its fixture fails it.
- The upgrade gap leaves the engineering spec.
