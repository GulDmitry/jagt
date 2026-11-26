## Why

A cold prompt the human types queued a fresh start behind the human's own just-submitted prompt, yet the hand-over line
was shown: no fresh session started and the task idled until the watchdog stopped it.

## What Changes

- A cold prompt the human types starts its fresh session at once; it never waits for the human.
- `relayUnderCold` tells whether a fresh session started; the hand-over line is shown only then.
- A fresh start jagt initiates keeps waiting for the human.
