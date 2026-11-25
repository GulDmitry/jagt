## Why

A posted note reads long and vague, its opening line is wordy, and a reviewer can only thumb it, so a rating never says what was wrong.

## What Changes

- The AI line is `AI note: 👍 helps, 👎 does not; reply with what is wrong.`; a note with the earlier line still counts as the role's.
- A note's body is the defect, its evidence as file:line and the fix, in at most three lines and 60 words, held by the brief and a `roleEval` case.
- A human reply under a note is recorded with the note's rating as its reason; a thumbs down with no reply is reason unknown.

## Capabilities

### New Capabilities

### Modified Capabilities

- `code-review`: the note's shape, the AI line, the reason in the dated statistics; the bot-answer and pre-ship requirements move to `review` to keep the spec within its line budget.
- `review`: takes those two requirements unchanged.
