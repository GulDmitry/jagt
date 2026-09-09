# Configuration

[← README](../README.md)

Everything is in **one file**: `jagt.yml` at the repository root — `cp jagt.yml.dist jagt.yml`. **Every key is
described in `jagt.yml.dist`, and nowhere else**: it carries each one with its shipped value and what that
value means, so the file you edit is the reference. Your copy is gitignored, so comment it freely.

One root, `orchestrator:`, holds all of it.

- `projects` is re-read on **every access**, so adding a repository needs no restart. Everything else Spring
  binds **once at startup**.
- An omitted key falls back to the defaults built into `orchestrator-backend/src/main/resources/application.yml`.
- A flag or environment variable (`--server.port=8390`, `LOG_FILE=…`) outranks both files, for one run.
- `config.json` is no longer read. If one is still lying around, jagt refuses to start and prints the
  `jagt.yml` to write in its place.

## Projects

The only section with no default, and the only one you must write:

```yaml
orchestrator:
  projects:
    widgets:
      path: /Users/you/work/widget-service
      baseBranch: origin/main
      deployBranch: dev
      labels: [widget, backend]
```

| key | meaning |
|-----|---------|
| `path` | absolute path to the base repository |
| `baseBranch` | where task branches are cut from. **Read-only**: jagt never pushes here |
| `deployBranch` | target of `deploy`; omit to disable deploy here. Equal to `baseBranch` is refused |
| `labels` | hints for mapping a ticket to this project when `do` names none |

## What a key does not say for itself

**`worktree.copyGlobs`** is one top-level list for every project, and what makes a fresh worktree buildable.
Widen it to whatever your tests and run configs read, knowing each copy is another copy of a credential in a
sibling directory. `**/` also matches at the repository root, so `**/.env` covers both `app/.env` and a
single-module repo's own `.env`.

**Binaries are resolved by bare name** — PATH, then the usual install directories (a GUI-launched process has
no Homebrew on PATH), then inside application bundles. Give an absolute path to pin one; for `editorCommand`
only the launcher is resolved, and the arguments stay yours.

**`server.address`** stays on loopback until you decide otherwise: the board asks for no password and can
deploy. Clients default to `127.0.0.1` rather than `localhost`, which resolves `::1` first and would cost a
refused connection per call.

**`assistant.mcpConfig` buys determinism, not money** — the figures are in [seams](rules/seams.md). Server
names lose their plugin scope here (`mcp__gitlab__…`, not `mcp__plugin_<x>_gitlab__…`), so an `allowedTools`
list written for the inherited names stops matching. Declare instead of inherit when the servers that can
answer are plugin-scoped or need an interactive login — the command that shows which is in
[Installation](installation.md).

**`CLAUDE_CONFIG_DIR` is read from jagt's own environment**, not the session's: exporting it in one shell and
starting jagt from another loses the derived path to a session's log.

**`autoReview`** polls every task with an open review request: approval advances it, comments are drafted for
you. **It never posts, pushes or deploys.** One model call per poll.

**`startupChecks`.** What is missing is [said at startup, all of it at
once](rules/components.md#what-is-missing-is-said-at-startup-not-at-the-click-that-needed-it). Nothing reaches
the network, so a token that is present but wrong surfaces at the first read instead. Test suites and smoke
scripts pass `false`: what is checked is your machine, and a CI runner is not one.
