## Why
A read that needs tools is the dearest call jagt makes; on a local model it costs nothing, and the owner chooses which reads trust one.

## What Changes
- `orchestrator.opencode.reads` names the roles whose read runs on OpenCode at the endpoint, each with its model.
- Startup refuses a read that names no installed role, has no endpoint, or resolves to no model.
