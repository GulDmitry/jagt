## Why

A Mac asleep stops the Master, the auto-review poll and intake; an install that wants them through a lunch break had no
way to say so.

## What Changes

- `keepAwake: true` holds a keep-awake assertion while unattended work is on, and releases it when that work goes off.
- Off by default: jagt is for the working day.

## Capabilities

### Modified Capabilities

- `unattended-work`: the machine stays awake on request while unattended work is on.
