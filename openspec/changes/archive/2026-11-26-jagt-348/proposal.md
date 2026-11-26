## Why

Red and green checks dots, a deployed verb, the open and Drafted tabs and a log chip next to the mode chip differ by colour alone,
and control borders on `--line` are 1.4:1 where WCAG 1.4.11 needs 3:1.

## What Changes

- `.checks.red` is a square, green stays a dot.
- `button.again` carries `↻` after its label.
- The tab underline means open only; the Drafted tab carries a `new` pill.
- A log chip carries `▸` before its name.
- A control's edge is `color-mix(in srgb, var(--text) 55%, var(--panel))`, at least 3:1 on `--panel` in both themes.
