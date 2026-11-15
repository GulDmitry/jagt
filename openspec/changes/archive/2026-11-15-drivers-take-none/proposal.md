## Why

A server or container has no display, no kitty and no editor; jagt had to be given a desktop it does not have.

## What Changes

- `terminal`, `notifier`, `keepAwake` and `editor` each take `none` in `jagt.yml`, chosen and never guessed.
- `focus` with `terminal: none`, banners with `notifier: none` and `open`/`diff` with `editor: none` answer with a sentence naming the key that enables them.

## Capabilities

### Modified Capabilities

- `platform`: keep-awake, the viewer driver and the editor take `none`; a new requirement for the notifier.
