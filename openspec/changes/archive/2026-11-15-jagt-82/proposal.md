## Why

One starter for desktop and server makes a server user read a desktop reference to find the dozen keys that differ.

## What Changes

- `jagt.yml.dist` is `jagt.desktop.yml.dist`, the full reference; `jagt.server.yml.dist` is a short ready server starter.
- The server starter describes nothing: each key points to its description in the desktop file.
- `ORCHESTRATOR_CONFIG_FILE` picks one file per environment; no profile, no overlay.

## Capabilities

### Modified Capabilities

- `startup`: the starter files and the per-environment file.
