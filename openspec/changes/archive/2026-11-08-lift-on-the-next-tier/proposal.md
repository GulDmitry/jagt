## Why
A stop on a failure the session could not turn green is lifted onto the same model that failed.

## What Changes
- An `escalate` verb lifts such a stop and starts the session again on the next model of `agent.tiers`.
