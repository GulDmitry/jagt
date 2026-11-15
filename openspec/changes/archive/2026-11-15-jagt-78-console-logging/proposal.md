## Why

A container's log is what `kubectl logs` and any collector read from stdout; jagt wrote ECS JSON to a file and a human pattern to the console.

## What Changes

- `orchestrator.logging`: `file` (default, as before) or `console`.
- `console` writes one ECS JSON object per line to stdout, key-value fields included, for the backend and the Master's feed, and no log file.
- Any other value is refused at start naming the key.

## Impact

- `startup`: gains "Logging is a seam: file or console".
