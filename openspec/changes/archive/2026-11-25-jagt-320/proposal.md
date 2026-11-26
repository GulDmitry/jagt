## Why

A server has nobody to finish a plugin server's browser login. A read that inherits the agent's own servers loaded them
anyway, and a server needing a login showed only after a read failed.

## What Changes

- `claude.disabledPlugins` also switches those plugins off for every read that inherits the agent's own servers.
- The head probes the agent's servers every ten minutes, not only after a failed read; a disabled plugin's server is no mark.

## Capabilities

### New Capabilities

### Modified Capabilities

- `server-environment`: a plugin server needing a browser login is switched off, or marked.
