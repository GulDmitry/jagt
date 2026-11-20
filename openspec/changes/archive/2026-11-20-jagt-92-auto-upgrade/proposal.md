## Why
A kept release waits for a human to press `upgrade`; an unattended install never moves.

## What Changes
- `orchestrator.releaseFeed.autoUpgrade` (default false): after each release check, a kept release is switched as `upgrade` does.
- Only under the launcher, and only while no agent session is up; otherwise it waits for the next check.
- A refused switch is not tried again for that version.
