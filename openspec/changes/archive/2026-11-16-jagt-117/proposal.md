## Why
In a pod nobody signs in through a browser: the agent's settings, plugins and MCP servers must come with the image, and a missing MCP config must show before a session needs it.

## What Changes
- A job reads each configured MCP config file; one missing or unreadable is a mark on the board's head.
- The entrypoint lays a baked seed folder over the agent's folder on every start.
