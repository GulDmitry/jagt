# 0084 — Code intelligence stays a jdtls per worktree

[← decisions](README.md) · rule: [`seams.md`](../rules/seams.md) ·
spec: [`runtime`](../../openspec/specs/runtime/spec.md) · relates: [0039](0039-jagt-starts-with-no-jvm-flag.md)

## Status

Accepted 2026-10-25. Researched 2026-10-19; sources not re-read since.

## Context

- Sources: JetBrains AI blog 2026-10-15 "How Netflix Made AI Coding Agents More Efficient with JetBrains IDE
  Intelligence" (`blog.jetbrains.com/ai/2026/10/how-netflix-made-ai-coding-agents-more-efficient/`, 403 to jagt, read via daily.dev);
  engine 1.0 post (`blog.jetbrains.com/idea/2026/10/intellij-idea-s-java-and-kotlin-for-cursor-and-vs-code-hits-1-0/`);
  docs (`jetbrains.com/help/intellij-vscode/get_started_vs_code.html`); Habr retelling (`habr.com/ru/companies/spring_aio/articles/1096226/`).
- Established: IntelliJ analysis as an LSP server in VS Code 1.105.1+ or Cursor (`JetBrains.intellij-server`); an editor is needed.
- Established: free in pre-release on 30-day builds, then an IntelliJ IDEA Ultimate subscription.
- Established: Java, Kotlin; Gradle, Maven, Bazel. No PHP; Python, TS, Go only as Netflix's next targets.
- Established: default heap 2 GB per server, no measured footprint.
- Established: Netflix's daemon + CLI + session manager (routes by project root) is unreleased.
- Established: navigation −15…60% tokens, end-to-end −10…30%, correctness held; rows per model unconfirmed.
- Unknown: shared index across worktrees; memory per server; no-editor run; JDK need; failed import; Claude Code or Codex install.

## Decision

- jagt keeps one jdtls per worktree; the IntelliJ engine is not adopted.
- The daemon an agent CLI would call is not public, only the editor extension.
- Routing by project root is a server per worktree: 24 × 2 GB, no gain over jdtls.
- A paid Ultimate seat breaks "getting started stays one copied file and one command".
- Nothing for non-Java projects.

## Rejected

- GraalVM native builds: jagt starts with no JVM flag of its own (0039).

## Reopen when

Any holds, checked at each refresh:

- JetBrains publishes the agent CLI or daemon, a Claude Code plugin or an MCP server on this engine.
- A source shows one server serving several worktrees of a repo.
- It runs with no editor installed.

Then: (1) one port, "a code-intelligence service a session is given", only `adapter/` names IntelliJ, jdtls or Serena,
per-language pick in `jagt.yml` + `jagt.yml.dist`; (2) order shared service, else per-worktree jdtls, else grep, a session
never fails to start for a missing tool; (3) measure first, same tasks with and without, from `usage-by-day.jsonl`;
(4) new record, `runtime` spec delta, retire `~/.claude/lsp/jdtls-launch` if replaced.
