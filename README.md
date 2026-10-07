<h1 align="center">jagt</h1>

<p align="center">
  <b>One ticket → one agent session → one Git worktree.<br>You approve every push.</b>
</p>

<p align="center">
  <a href="https://github.com/GulDmitry/jagt/actions/workflows/ci.yml"><img alt="CI" src="https://github.com/GulDmitry/jagt/actions/workflows/ci.yml/badge.svg"></a>
  <img alt="Java 25+" src="https://img.shields.io/badge/Java-25%2B-orange">
  <img alt="macOS · Linux" src="https://img.shields.io/badge/macOS-·%20Linux-lightgrey">
  <a href="LICENSE"><img alt="Apache 2.0" src="https://img.shields.io/badge/license-Apache--2.0-blue"></a>
</p>

jagt hands a ticket to an autonomous AI coding agent in its own isolated Git worktree, so two agents cannot see
or break each other's work. You drive all of them from one board in your browser.

**Nothing leaves your machine without you.** No push, no merge request, no deploy.

```mermaid
flowchart LR
    you(["👤 you"]) -- "board" --> jagt["<b>jagt</b><br/>tasks · flow · Master"]
    jagt -- "spawns" --> s["<b>agent sessions</b><br/>in tmux"]
    jagt -- "cuts, deploys" --> wt[("<b>git worktrees</b><br/>one per task")]
    s -- "MCP: status" --> jagt
    s -- "code, own branch" --> wt
    s -- "MCP" --> host["code host · tracker"]
```


## The session, and you

| the agent session | you |
|---|---|
| reads the ticket, writes the code, runs your tests | look at the live diff — `ide` |
| leaves **everything uncommitted** | `ship` — the first commit and push there is |
| fixes the CI failures and the comments, drafts every reply | read the drafts — `replies` — then `ship` |
| touches no shared branch and no other task | `deploy`, then `done` |

One session, one worktree, one branch, one request; the server, not a prompt, keeps it out of other tasks.

## Start

Every tool it needs: **[Technologies](docs/installation.md)**.

### 1 — check MCP access

jagt reads tickets and review rounds through a **headless** Claude Code call on *your* MCP servers; one behind
an interactive login, or from a plugin, does not answer it, though `claude mcp list` says "connected":

```sh
cd "$TMPDIR" && claude "Name your MCP tools for <your tracker> and <your code host>, or say NONE." -p
```

`NONE` means jagt cannot read a ticket yet; [Installation](docs/installation.md) has the fix. Any agent CLI may
write the code.

### 2 — clone, and name one repository

```sh
git clone https://github.com/GulDmitry/jagt.git && cd jagt
cp jagt.yml.dist jagt.yml
```

`projects` is the only section with no default — without one jagt refuses to start:

```yaml
orchestrator:
  projects:
    widgets:
      path: /Users/you/work/widget-service   # absolute path to the repository
      baseBranch: origin/main                # tasks are cut from here, and it is never pushed to
      deployBranch: dev                      # omit to disable `deploy` for this project
  worktree:
    copyGlobs: ["**/.env"]                   # untracked files a fresh worktree needs to build
```

**`copyGlobs` makes a worktree usable**: a clean checkout lacks whatever your build reads but git does not
track. Widen it until your tests pass inside one; each pattern copies those secrets beside the repository.
Every other key has a default, described in `jagt.yml.dist`.

### 3 — decide on the Master

Keep `master.mode: off` at first: every press stays yours. `judge` adds a model's review before
yours; `act` also presses for you — [Usage](docs/usage.md#the-master).

### 4 — build, and run

```sh
cd orchestrator-backend
./gradlew build stageJar
java -jar build/libs/jagt-run.jar
```

Anything missing, and jagt refuses to start with the **whole** list, each line naming the key that fixes it.

> [!IMPORTANT]
> Run the **staged** `jagt-run.jar`. `./gradlew build` rewrites `jagt.jar` in place, and a JVM still reading it
> dies with a `NoClassDefFoundError` that hides the real error — [Troubleshooting](docs/troubleshooting.md).

### 5 — open the board

**http://localhost:8290** — type a ticket key or URL in the first field, press **Start**.

## Commands

| command | what it does |
|---------|--------------|
| `do ABC-42` | read the ticket, cut a worktree, launch a session |
| `ide ABC-42` | open the worktree in your editor — the live diff |
| `focus ABC-42` | jump into the session and talk to it |
| `ship ABC-42` | commit, push, open or update the review request |
| `sweep ABC-42` | pull checks + comments; the agent fixes locally and drafts replies |
| `replies ABC-42` | read those drafted replies before they go out |
| `deploy ABC-42` | merge the task branch into the deploy branch |
| `done ABC-42` | close the task and clean everything up |

Each is a board button too; `Help` explains its marks. A `⌘K` sentence is mapped
onto exactly one of them, through the same gate the button uses. `revert`, `respawn`, `resume`, `diff`,
`stats`, `activity` and `jobs` are in [Usage](docs/usage.md).

## What you keep

- The base branch is **read-only**; only `deploy` and its undo `revert` write a shared branch, both yours to
  trigger and neither rewriting history.
- **Nothing is written into your project's tracked files** — no git hook, nothing to uninstall.
- Sessions live in tmux and the state is one JSON file, so restarting the backend loses nothing; a reboot
  costs the windows, and `focus` re-enters each session where it left off.
- **Four things cost a model call**: a ticket, a merge request, a review round, a `⌘K` sentence.
- The board asks for no password and can deploy, so it stays on `127.0.0.1`.

It does not run your CI, hold a credential, or run on Windows.

## Documentation

| | |
|---|---|
| [Installation](docs/installation.md) | every technology, per platform, and the first run |
| [Usage](docs/usage.md) | the board, every command, the review loop |
| [Configuration](docs/configuration.md) | where a setting goes, and what outranks what |
| [Troubleshooting](docs/troubleshooting.md) | symptom → cause → fix |
| [Development](docs/development.md) | test suites, CI, running the Linux suite from a Mac |
| [Architecture](ARCHITECTURE.md) | the code map: what kinds of thing jagt has, and where a new one goes |
| [C4](docs/c4.md) | the same, drawn: context, containers, rings, hooks, Master |

## License

[Apache 2.0](LICENSE)
