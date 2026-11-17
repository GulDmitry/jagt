# 0195 — The terminal library is vendored past 50 KB and loaded on demand

[← decisions](README.md) · rule: [`design.md`](../rules/design.md), [`board`](../../openspec/specs/board/spec.md)

## Status

Accepted 2026-11-17; reopens [0046](0046-the-board-renders-preact-components-with-no-build.md)'s "vendor files past 50 KB",
as [0173](0173-a-code-editor-is-vendored-and-loaded-on-demand.md) did.

## Context

- A live terminal needs an emulator: escape sequences, cursor, selection, resize, keys.
- Measured 2026-11-16, npm registry, ES module files byte for byte:

| file | raw | gzip |
|------|-----|------|
| `@xterm/xterm` 6.0.0 `lib/xterm.mjs` | 345 KB | 88 KB |
| `@xterm/xterm` 6.0.0 `css/xterm.css` | 7 KB | - |
| `@xterm/addon-fit` 0.11.0 `lib/addon-fit.mjs` | 2 KB | - |

- Both modules import nothing, so no import map is needed.

## Decision

- The files sit in `board/vendor/xterm/` as the registry served them; the licence in `board/vendor/LICENSE-xterm`.
- `board/terminal/` `import()`s them by URL when the Session tab first opens: the first paint fetches none.
- `BoardAssetsTest` pins each file's SHA-256 and leaves `vendor/xterm/` and `terminal/` out of the 224 KB budget; the number stays.
- The renderer is the DOM one xterm ships: no addon beyond fit.
- Bumping a package replaces its file and its pin together.

## Rejected

- A bundle: a build step and a lock file, against [0170](0170-the-board-is-its-own-app-behind-one-contract.md).
- A CDN: AGENTS.md forbids external assets.
- Loading with the page: 88 KB gzip on every first paint for a tab most sessions never open.
- A canvas or WebGL addon: more vendored bytes for speed nobody measured as short.

## Reopen when

- The DOM renderer drops frames on a measured busy window.
- A xterm release ships no ES module, or its module imports a bare name.
