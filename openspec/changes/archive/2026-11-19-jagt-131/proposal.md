## Why
CI skips the absolute budgets, so a slowdown on a shared runner goes unseen.

## What Changes
- The server scenario records startup and `/state` p95; the stand job compares them with their history and fails past 200 %.
