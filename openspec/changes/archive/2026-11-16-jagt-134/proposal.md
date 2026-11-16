## Why
jagt's gates were scattered across rules, decisions and a task's notes; a project copying them had no one list to check.

## What Changes
- A capability `engineering` states every gate of jagt's practices stack-agnostically, each scenario naming its check per stack.
- A gate a stack lacks is named a gap with its task; `EngineeringSpecTest` fails when a scenario names a test class that does not exist.
