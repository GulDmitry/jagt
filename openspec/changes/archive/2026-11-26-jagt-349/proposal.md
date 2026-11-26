## Why

The Help legend lacked the stale page, the open tab, the link marks, and its sample broken badge read as a need;
nothing checked a mark class against `design.md`.

## What Changes

- `DesignMarksTest` scans `app.css`, the legend and `design.md`: each mark class is named in `design.md` and sampled in the legend.
- Every selector `design.md` names exists in `app.css`.
- Legend rows added: the open tab, a link, the stale page; the broken badge sample reads an act.
