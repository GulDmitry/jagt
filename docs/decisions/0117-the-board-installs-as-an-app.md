# 0117 — The board installs as an app

[← decisions](README.md) · rule: [`board`](../../openspec/specs/board/spec.md#requirement-components-ship-as-the-files-that-run)

## Status

Accepted 2026-10-31.

## Context

- The board is one page in a browser tab, wanted in its own window with a Dock icon.
- A shell serving `static/` from `tauri://`, `file://` or `app://` sends a foreign Origin: `LoopbackFilter` refuses every write.
- Checked 2026-10-31: `electron-v45.2.1-darwin-arm64.zip` is 120.8 MB before the app and the JVM.
- Checked 2026-10-31: Tauri on Linux needs `webkit2gtk-4.1`; a native shell adds per-OS runners and a signing identity.
- [0021](0021-jagt-runs-from-a-clone-of-its-repository.md): no installer or package until users who are not developers need one.
- [0020](0020-jagt-holds-no-token-of-its-own.md): jagt holds no credential, a signing identity would be the first.

## Decision

- The jar serves `manifest.webmanifest` and two PNG icons; `index.html` links the manifest.
- The window is the browser's: Chrome "Install page as app", Safari "Add to Dock".
- No service worker (the board is useless offline) and no config key.
- Colours are the palette's own: `--bg` as background, `--accent` as theme.
- `java -jar` stays; the installed window starts and stops nothing.

## Rejected

- Electron: 120 MB per artefact, Node and electron-builder in a Java build, and a signing identity.
- Tauri: the sidecar is a launcher for a JAR, plus a Rust toolchain, three artefacts and signing.
- WKWebView app: macOS only, a Swift project, signing.
- jpackage with a WebView: per-OS runners, and `stageJar` no longer applies.

## Reopen when

- The window must own the JVM: 0021's trigger, users who are not developers, then weigh Tauri against jpackage on a measured image size.
- Unverified: whether Chrome offers the install prompt on `http://localhost` and `127.0.0.1`; settle by a manual install and record which one works.
