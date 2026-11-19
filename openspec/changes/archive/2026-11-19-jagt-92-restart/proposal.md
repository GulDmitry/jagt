## Why
An upgrade that leaves the human to restart jagt by hand leaves the old version running.

## What Changes
- Started by the launcher, `upgrade` stops jagt gracefully with exit code 75; the launcher starts the version `current` names.
- Not started by the launcher, it exits nothing and says to restart.
- The launchers loop while the jar exits 75 and pass any other code through.
