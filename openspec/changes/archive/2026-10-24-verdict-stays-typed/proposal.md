## Why

The Master's typed answer was rendered to `master-review.md` and parsed back by lines; a repeat was a word overlap of
two findings; a `do` line was found by its prefix; a verdict answered whichever hand-back came before its file's
time, and the counts of repeats and answers lived in memory, lost on a restart (decision 0072, step 4).

## What Changes

- Every role's answer is stored as written, `.jagt/master-review.json`; `master-review.md` beside it is never read back.
- A verdict carries the hand-back jagt asked about and answers that one alone, compared by equality.
- A finding names its file, anchor and kind; it repeats when the three come back, however worded.
- Work the Master would open as a task is its answer's `newTask`, never a prefix in its words.
- Repeated rounds and answers over a tree are kept in `.jagt/master-rounds.json`, across a restart.

## Capabilities

### New Capabilities

### Modified Capabilities

- `master`: the verdict stays typed from the answer to every reader.
