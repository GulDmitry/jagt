## Why
Acceptance runs Playwright inside the organisation's image; a missing `npx` fails every check late.

## What Changes
- The program read also covers an enabled acceptance role's `flows.command` when its driver is playwright.
