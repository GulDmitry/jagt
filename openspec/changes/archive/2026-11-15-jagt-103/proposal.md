## Why

A parent started with SIGINT ignored, such as a daemon born in a background shell, handed the ignore to every app jagt launched, so `kill -INT` stopped none of them.

## What Changes

- A detached launch resets SIGINT to its default before the app starts.

## Impact

- `runtime`: modifies "A detached launch gets its own session".
