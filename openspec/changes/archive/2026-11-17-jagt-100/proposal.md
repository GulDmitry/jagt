## Why
A history entry records origin `ACCEPTANCE` without the checker's role id, so the board labels every checker's round "acceptance".

## What Changes
- The step an acceptance round takes carries the checker's role id in `state.json` history (`role`).
- The board's history names that role in place of "acceptance"; an entry without it keeps "acceptance".
- No role display name: `name` in `role.yml` is read by nothing, so the id is the label.
