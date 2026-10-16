# A desktop host for jagt — research, 2026-10-16

Is an IntelliJ plugin or a desktop app worth building, or is a diff and merge inside the board enough?
`[sN]` points to [Sources](#sources). A line without a source is this document's own estimate.

## 1. TL;DR

1. **No IntelliJ plugin and no own desktop app now.** jagt uses IntelliJ for two launches only, both behind
   `EditorDriver`: `ide` opens a worktree, and `diff` compares two directories (`IdeDiff`). IntelliJ is already optional.
2. **Review in the board**: the server runs `git diff`, and the board renders it with plain JS in the jar.
   Line comments go into the review round. This fits "no build step, no CDN" and adds no second surface.
3. **Merge stays out of the board**: the session resolves the conflict in its own worktree, and the human reviews
   the diff in (2). A manual 3-way merge is one more launcher, `editorMergeCommand`, beside `editorDiffCommand`.
4. **Reopen the plugin** only when the review needs IDE navigation (go to definition, find usages) next to the
   diff. Then the plugin hosts the board and adds nothing of its own.

## 2. What jagt needs from an IDE today

| need | today | IntelliJ needed? |
|------|-------|------------------|
| open a task's worktree | `editorCommand` → `idea <path>` | no: any launcher |
| compare base with the task | `editorDiffCommand` → `idea diff <l> <r>` | no: `code --diff`, `meld`, `opendiff` |
| read the diff to judge it | a separate window, outside the board | — this is the gap |
| 3-way merge on a conflict | nothing; the session resolves it | no |

## 3. Options

### A. Diff in the board — recommended

- Source: `git diff --no-ext-diff -U<n> base...task`, the same text `MasterPanel` already reads for a round.
- Render: unified and side-by-side, word-level marks from `--word-diff=porcelain`, all as text and borders.
  No library: diff2html, Monaco and `@codemirror/merge` each need either a bundle or a CDN.
- A comment on a line becomes a finding of the round, so this replaces the round on the code host for jagt's own tasks.
- Cost: one view and one read endpoint; `boardTest` covers it. It replaces the `diff` launch as the default.
  The launcher stays for whoever wants a real IDE.
- Limit: no navigation and no syntax highlighting beyond plain text.

### B. Manual merge

- `git merge-file` / `checkout --conflict=zdiff3` give base, ours and theirs as files. A launcher gets four
  paths: `idea merge`, `code --merge`, `meld`, `opendiff -ancestor -merge`.
- A 3-way merge editor in the board costs more than everything in A, for a step a session usually does itself.

### C. IntelliJ Platform plugin

- Shape: a tool window with `JBCefBrowser` loading `localhost:8290`, plus `DiffManager` for the native diff [s1, s2].
  Spring stays in the jagt process. The plugin is a thin client and keeps no state.
- Compatibility: GigaIDE 2025.1 is on platform 2025.1 [s5, s6]. OpenIDE 2025.3 is on 2025.3, and its runtime
  ships JCEF [s3, s4]. Community has had no separate build since 2025.3 [s7].
  So `sinceBuild = 251`, stable APIs only, and the Plugin Verifier runs against all three [s8].
- Delivery: the JetBrains Marketplace is blocked in Russia. OpenIDE and GigaIDE each run their own marketplace,
  and GigaIDE accepts a custom repository URL [s4, s6]. jagt can serve its own `updatePlugins.xml`.
- Cost: a second Gradle build (IntelliJ Platform Gradle Plugin, Groovy DSL works), a three-IDE verifier
  matrix, and a fix for each platform break. 2026.1 changed input handlers and split the Java plugin [s9].
- Risk: jagt starts to look like an IDE assistant. ACP already puts Claude Code and Codex in the IDE chat [s10].
  jagt orchestrates across worktrees, and that does not fit in a chat panel.

### D. Own desktop app

- jpackage plus a webview (JavaFX `WebView`, or a Chromium wrapper) gives a dock icon and a window.
- `chrome --app=http://localhost:8290` gives the same today at no cost. Nothing in the board needs a native API.
- Cost: installers per OS, signing and notarisation on macOS, and updates. None of it is about agents.

## 4. Open

- Do line comments in A replace the code host's review for jagt's own tasks, or only feed the session?
- "Research inside jagt": a task whose outcome is a document, not a diff. Is it a new task kind, or a plan with no code?

## Sources

- [s1] JCEF in plugins — https://plugins.jetbrains.com/docs/intellij/embedded-browser-jcef.html
- [s2] Differences viewer — https://www.jetbrains.com/help/idea/differences-viewer.html
- [s3] Axiom JDK, 8 questions about OpenIDE (runtime with JCEF, plugins compatible) — https://axiomjdk.ru/blog/openide
- [s4] OpenIDE: Community fork, own marketplace (350+ plugins), AGPL — https://ru.wikipedia.org/wiki/OpenIDE
- [s5] GigaIDE 2025.1 on IntelliJ 2025.1 — https://habr.com/ru/companies/spring_aio/news/968848/
- [s6] GigaIDE 2025.1: custom marketplace, `idea.plugins.host` — https://habr.com/ru/articles/968808/
- [s7] IntelliJ IDEA unified distribution — https://blog.jetbrains.com/idea/2025/07/intellij-idea-moves-to-the-unified-distribution/
- [s8] Verifying plugin compatibility — https://plugins.jetbrains.com/docs/intellij/verifying-plugin-compatibility.html
- [s9] Incompatible API changes 2026 — https://plugins.jetbrains.com/docs/intellij/api-changes-list-2026.html
- [s10] ACP in AI Assistant — https://www.jetbrains.com/help/ai-assistant/acp.html
