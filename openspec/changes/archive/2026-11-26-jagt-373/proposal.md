## Why

A session asked "ship?" at `REVIEW_PENDING`; the Master answered "ship" as text although it holds `ship`. The session went back to
`VERIFYING` and re-ran every probe on a tree that was already green.

## What Changes

- An answer to a session's question that decides a move the Master holds (`ship`, `deploy`) presses that move and relays no text.
- The press goes through the same door and the unattended-pace count as any Master press.
- A tree or drafts that changed since the Master read them leave the move unpressed; the answer is relayed and the next hand-back is read anew.
- The session brief says a ready tree hands back `REVIEW_PENDING` and never asks whether to ship.
