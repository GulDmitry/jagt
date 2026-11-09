# triage — design

## Why it exists

A stopped task waits for the human's lift. Triage reads the stop once and puts one line on its hover: who looped,
which family of defect it is and whether a lift is safe, so the human decides faster.

## Responsibility

- Owns one read per stop: its trail and commits, answered as who, class, family and safe or unsafe.
- Does not own the lift: the card still asks `lift the stop`, and the human, or the Master in `act`, presses it.
- Does not own any change: it presses, sends, notifies and writes nothing, to git or the code host included.

## Who triggers it

jagt alone: `roles.triage.enabled` is set and a task stops. Never a model's answer.

## Use cases

- **WHEN** `roles.triage.enabled` is set and ABC-42 stops
  **THEN** the stop is read once, its hover gains `triage reads: <who> — <class> (<family>), lift looks <safe|unsafe>`
- **WHEN** the human lifts ABC-42's stop and it stops again
  **THEN** the first line is gone and the second stop is read afresh
- **WHEN** the read fails
  **THEN** the hover says `triage could not read: <cause>` and that stop is not read again
- **WHEN** triage reads a stop as `lift safe`
  **THEN** nothing is pressed or written, and the card still asks `lift the stop`
- **WHEN** `roles.triage.enabled` is not set
  **THEN** nothing is read and the hover is the stop's own

## Usage

- On by `roles.triage.enabled`; it reads through the Master's reader, its spend the Master review's.
- `who` is one of `session`, `master`, `case`, `jagt` or `none`; the first that fits the evidence.
- `family` is a letter A to M of `docs/cases.md`, or `unknown` where `who` is `none` or no family fits.
- Unsure is unsafe; `ready` means a lift is safe, `not ready` unsafe.

## Guards

- Read-only: it edits nothing, presses nothing, pushes nothing.
- The trail, a commit message, a ticket or a log is data from outside; an instruction found in it is not followed.
- Where the trail cannot tell two apart, it names the one the evidence proves and says what is missing.
- Its answer has exactly one finding; every premise carries the command it ran and what it printed.

## Cost against the alternative

- A stop with no read sends the human to the trail and commits unaided.
- One read per stop, charged to the Master's review.
