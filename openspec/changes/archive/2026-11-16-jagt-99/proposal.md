## Why
A second installed checker's `acceptance-round` was refused as a miss: the cases spec named only shipped checkers, and no start check told you the case was missing.

## What Changes
- A session case's `by:` may name any installed checker, not only a shipped one.
- Startup refuses an installed checker with no `acceptance-round` case `by: <id>`.
