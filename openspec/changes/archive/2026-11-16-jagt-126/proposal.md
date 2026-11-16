## Why
The board answered with no Content-Security-Policy: a script from anywhere would run in a logged-in board.

## What Changes
- Every response carries a Content-Security-Policy naming the board's own origin.
- The board's pages run no inline script but their import map, whose hash the policy names.
