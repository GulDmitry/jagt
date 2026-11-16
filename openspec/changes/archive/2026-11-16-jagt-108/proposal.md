## Why
A project's `verifyCommand` or `setupCommand` naming a program the image lacks failed only when a task reached it.

## What Changes
- A job resolves the first word of each project's `verifyCommand` and `setupCommand`; a program not found is a mark on
  the board's head naming the project, the program and the fix, and jagt keeps running.
