## Why
A jagt restart inside its container stopped the container, killing its tmux and every session; on a host tmux outlives jagt.

## What Changes
- The shipped image runs jagt under an init and a relaunch loop: the JVM exiting leaves tmux running and starts jagt again.
- SIGTERM to the container still stops it.
