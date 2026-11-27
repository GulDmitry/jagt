# Configuration

[← README](../README.md)

Everything is in **one file**: `jagt.yml` at the repository root — `cp jagt.desktop.yml.dist jagt.yml`. **Every key is
described in `jagt.desktop.yml.dist`, and nowhere else**: it carries each one with its shipped value and what that
value means, so the file you edit is the reference. Your copy is gitignored, so comment it freely.
Both `.dist` files validate against a JSON Schema generated from the configuration records in `ConfigReferenceTest`.

One root, `orchestrator:`, holds all of it.

- The sections `jagt.desktop.yml.dist`'s header names are re-read on **every access**, so adding a repository or
  changing `roles.master.mode` needs no restart. Everything else Spring binds **once at startup**.
- An omitted key falls back to the defaults built into `orchestrator-backend/src/main/resources/application.yml`.
- A flag or environment variable (`--server.port=8390`, `LOG_FILE=…`) outranks both files, for one run, but only
  for the keys read once at startup; the re-read sections `jagt.desktop.yml.dist`'s header names are file-only.
- A string takes `${VAR}`, `${VAR:default}` and `${file:/path}` (a mounted secret, one trailing newline cut); then every
  `JAGT__a__b=value` variable sets the key `a.b` (a double underscore per level, camelCase kept, from the document's root:
  `JAGT__orchestrator__conflictStallMinutes`, `JAGT__server__port`). Both hold in the re-read sections and in the keys Spring binds.
  A variable unset with no default refuses the start, naming the key and the variable; a resolved value is never logged.
- `config.json` is no longer read. If one is still lying around, jagt refuses to start and prints the
  `jagt.yml` to write in its place.

## Projects

The only section with no default, and the only one you must write:

```yaml
orchestrator:
  projects:
    widgets:
      path: /work/widget-service
      baseBranch: origin/main
      deployBranch: dev
      labels: [widget, backend]
```

| key | meaning |
|-----|---------|
| `path` | absolute path to the base repository |
| `baseBranch` | where task branches are cut from. **Read-only**: jagt never pushes here |
| `deployBranch` | target of `deploy`; omit to disable deploy here. Equal to `baseBranch` is refused |
| `labels` | ticket labels placing an item in this project where `do` names none; a label naming two projects goes to the router |
| `about` | one line saying what this repository is, for placing an item its labels do not |
| `remote` | the git remote every fetch, push and tracking ref uses: `origin` (default), another name, or `none` for a repository kept on this machine; `none` needs `codeHost: none` |
| `codeHost` | `github` (default), `gitlab`, `command`, `none` or `replay`: what jagt reads this project's deploy pipeline through, `none` also shipping with a push and no request; wins over the top-level `codeHost` |
| `codeHostName` | the code host's name where the remote does not give it, such as a self-hosted GitLab or GitHub Enterprise |
| `setupCommand`, `setupMinutes` | an argv run once in every new worktree of the project, after `worktree.copyGlobs` and before the session starts, `{branch}` and `{worktree}` filled; a non-zero exit or `setupMinutes` (20 where unset) fails the start naming the project and the output's tail. Unset, nothing runs |
| `companions` | projects every task started on this one also works in, one worktree and one request each; one that is not a project refuses startup |
| `awaitingCommand`, `changeCommand`, `postCommand`, `reviewBots` | `codeHost: command` only: commands printing the open requests awaiting review (`{remote}`) and a request's change (`{request}`) as JSON, and posting a note (`{request}`, `{replyTo}`, the text last); the accounts that are bots, beside the host's own word and the top-level `codeReview.reviewBots` |
| `pipelineCommand`, `retryCommand`, `closeCommand`, `notesCommand`, `loginCommand` | `codeHost: command` only: a command printing `success`, `failed`, `running`, `none` or `unknown` for `{sha}` ; one re-running `{pipeline}`; one closing the request at `{request}`; one printing the ids of its unresolved review notes, one per line (unset, a round with threads is never read as a repeat), or for the code reviewer a JSON array of notes with `id`, `author`, `body`, optional `thread`, `path`, `resolved`, `mine`, `bot`; one the board's `login` opens when any exits 77 (signed out) |

## What a key does not say for itself

**`data`** is the one folder jagt writes in ([the tree](rules/components.md#the-data-folder)): `~/.jagt/data`,
or an existing `<root>/data` with a state; `jagt.yml` stays outside it.
A task's worktrees are `data/tasks/<task>/worktrees/<project>`.
A project's own `path` is used in place, and the folder holding it must stay writable for `deploy`.

**`worktree.copyGlobs`** is one top-level list for every project, and what makes a fresh worktree buildable.
Widen it to whatever your tests and run configs read, knowing each copy is another copy of a credential in a
sibling directory. `**/` also matches at the repository root, so `**/.env` covers both `app/.env` and a
single-module repo's own `.env`.

**Binaries are resolved by bare name** — PATH, then the usual install directories (a GUI-launched process has
no Homebrew on PATH), then inside application bundles. Give an absolute path to pin one; for `editorCommand`
only the launcher is resolved, and the arguments stay yours.

**`server.address`** stays on loopback unless `auth.mode` is `password`: with `none` the board asks for no
password and a wider address refuses the start ([remote access](remote-access.md)). Clients default to
`127.0.0.1` rather than `localhost`, which resolves `::1` first and would cost a refused connection per call.

**`server.publicUrl`** is the address a human opens the board at (a pod's ingress, a mesh name); every link in a banner or
channel message starts with it, and with it blank they name `http://localhost:<port>`. The sessions' own URLs
(mcp, hook, gate) stay on loopback.

**`assistant.mcpConfig` buys determinism, not money** — the figures are in [seams](rules/seams.md). Server
names lose their plugin scope here (`mcp__gitlab__…`, not `mcp__plugin_<x>_gitlab__…`), so an `allowedTools`
list written for the inherited names stops matching. Declare instead of inherit when the servers that can
answer are plugin-scoped or need an interactive login — the command that shows which is in
[Installation](installation.md).

**`assistant.local` is opt-in**, and the server is yours to run: [Local models](local-models.md) has the quick start
per memory size and what a local model serves.

**`CLAUDE_CONFIG_DIR` is read from jagt's own environment**, not the session's: exporting it in one shell and
starting jagt from another loses the derived path to a session's log.

**`autoReview`** polls every task with an open review request: approval advances it, comments are drafted for
you. **It never posts, pushes or deploys.** One model call per poll.

**`deploy.onApproval`** deploys an approved request once, with the guards of your own `deploy`; off by default,
on in `jagt.server.yml.dist`. The history names the code host as who acted.
**`deploy.requireApproval`** offers `deploy` only once the host approves, never on a reviewed request; off by default,
on in `jagt.server.yml.dist`.

**`startupChecks`.** What is missing is [said at startup, all of it at
once](rules/components.md#what-is-missing-is-said-at-startup-not-at-the-click-that-needed-it). Nothing reaches
the network, so a token that is present but wrong surfaces at the first read instead. Test suites and smoke
scripts pass `false`: what is checked is your machine, and a CI runner is not one. jagt's own files, the
cases, are checked either way.

**`visualize`** draws and stores nothing in jagt: the session writes `task_visual.md` into its worktree, the board
reads it on request, and it goes with the worktree. A small fix gets no picture.
