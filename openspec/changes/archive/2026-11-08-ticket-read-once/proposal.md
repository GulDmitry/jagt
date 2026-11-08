## Why

The model's copy of a ticket is not byte-stable. A second read changes the Master's inputs digest and asks the panel again over an unchanged round.
Its spend is also booked as a Master review, so ticket reads and the panel's calls cannot be told apart in the ledger.

## What Changes

- A task's ticket is read once and kept beside `state.json`; a second working stint and a restart read nothing.
- The ticket text read is booked as `TICKET_TEXT`, no longer `MASTER_REVIEW`; the replay recording key and `mcpConfigFor` follow it.
- Each charged Master call logs one line naming its kind.

## Impact

- Spec: `unattended-work`.
- `TicketTexts`, `AssistantCallKind`, `HeadlessClaudeTrackerAssistant`, `ReplayTrackerAssistant`, `ChargedReviews`, stand recordings.
