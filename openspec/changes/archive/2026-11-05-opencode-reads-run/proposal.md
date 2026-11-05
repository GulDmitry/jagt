## Why
The roles named under `orchestrator.opencode.reads` have their read run on a local model; the read must stay read-only whatever that model does.

## What Changes
- The QA check read and the Master's round reads run `opencode run` on the endpoint for each role named under `reads`.
- Each read is held to the role's tool scope twice: by deny-first permissions, then by its tool calls after the run.
