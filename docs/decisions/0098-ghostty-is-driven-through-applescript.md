# 0098 — Ghostty is driven through AppleScript

[← decisions](README.md) · rule: [`runtime`](../../openspec/specs/runtime/spec.md)

## Status

Accepted 2026-10-28.

## Context

kitty was the one `TerminalDriver`. A viewer must be addressable: a tab opened in one window, raised and closed by
title, never by keystroke. Ghostty 1.3 ships an AppleScript dictionary (`Ghostty.sdef`) with windows, tabs, a
`name override` per tab and `new surface configuration` carrying a command and a directory.

## Decision

- `orchestrator.terminal` selects the driver: `kitty` (default) or `ghostty`.
- `GhosttyTerminalDriver` addresses a viewer by the `name override` of its tab, as kitty does by its tab title.
- macOS only: a Linux host with `ghostty` is refused at startup, naming the key.
- A reveal or close never launches Ghostty.

## Rejected

- Keystrokes or `open -na ghostty`: neither addresses a tab, and keystrokes land in whatever is focused.
- Ghostty on Linux: it has no addressable window or tab interface there.

## Reopen when

- Ghostty ships an addressable window and tab API on Linux.
