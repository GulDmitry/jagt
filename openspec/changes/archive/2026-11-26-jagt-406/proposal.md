## Why

A session filed a still-running pipeline as `outcome=wait` three times, which stopped a task whose CI was merely slow.

## What Changes

- The session brief says a request's pipeline still running is jagt's to poll: CI_POLLING, never a wait.
