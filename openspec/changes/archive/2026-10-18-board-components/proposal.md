## Why

The board was hand-built DOM with its own diffing, no page showed a component's states, and nothing recorded which
controls a human uses.

## What Changes

- Components are Preact with `htm`, vendored and pinned, with no build step; the card is the first.
- `styleguide.html` draws every component in every state.
- The board logs each press as a control and a task status, under a ceiling.

## Capabilities

### Modified Capabilities

- `board`: components without a build, a styleguide, a press log.
- `guards`: the press log has a ceiling.
