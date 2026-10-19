## Why

A session that stops on a full usage window stays stopped until a human notices the reset.

## What Changes

- A turn ended by a usage limit reports through its own hook event.
- With `usageLimits.wakeOnReset` (default false), one resume line is said to that session at the window's reset.

## Capabilities

### Modified Capabilities

- `runtime`: a usage-limit stop is a typed report.
- `usage-limits`: a stopped session is woken once at the reset.
