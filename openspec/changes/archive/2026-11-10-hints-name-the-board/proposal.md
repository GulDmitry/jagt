## Why

The board is used from a phone; a hint that sends the human to the agent's window or to the laptop cannot be followed there.

## What Changes

- A hint, a card's next-step label and a notification name the board's Session tab or a board verb, never `focus` or "by hand".
- Where the hint needs a diff to read, it still names the `ide` verb until the board has a Diff tab.

## Impact

- Specs: `board` (the verbs requirement).
- Code: `flow/Move`, `job/IntakeHistory`; tests pinning their text.
