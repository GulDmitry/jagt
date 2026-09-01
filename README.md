<h1 align="center">jagt</h1>

<p align="center">
  <b>One ticket → one AI agent → one Git worktree.<br>You approve every push.</b>
</p>

<p align="center">
  <a href="https://github.com/GulDmitry/jagt/actions/workflows/ci.yml"><img alt="CI" src="https://github.com/GulDmitry/jagt/actions/workflows/ci.yml/badge.svg"></a>
  <img alt="Java 25+" src="https://img.shields.io/badge/Java-25%2B-orange">
  <img alt="macOS · Linux" src="https://img.shields.io/badge/macOS-·%20Linux-lightgrey">
  <a href="LICENSE"><img alt="Apache 2.0" src="https://img.shields.io/badge/license-Apache--2.0-blue"></a>
</p>

jagt hands a ticket to an autonomous AI coding agent — [Claude Code](https://claude.com/claude-code) by
default, or Codex — running in its own isolated Git worktree, so two agents cannot see or break each other's
work. You drive all of them from one place: a board in your browser.

**Nothing leaves your machine without you.** No push, no merge request, no deploy.

Agents write code faster than humans can review, check and release it. jagt is the console for those three
[human gates](#your-three-checkpoints), for as many agents as you can keep reviewed.

```mermaid
flowchart LR
    DO["do ABC-42"] --> IDE["ide"] --> SHIP["ship"] --> SWEEP["sweep"] --> DEPLOY["deploy"] --> DONE["done"]
    SWEEP -.->|"another round"| IDE
    classDef c font-family:monospace,fill:#1a1a2e,color:#7ee787,stroke:#7ee787;
    class DO,IDE,SHIP,SWEEP,DEPLOY,DONE c;
```

## Start

**Check this first.** jagt reads tickets through *your* MCP servers, in a background session that cannot log
in. Servers needing a browser login, or coming from a plugin, will not answer it — and `claude mcp list` still
says "connected". [One command to check](docs/installation.md#mcp-access-comes-first).

You also need Java 25, tmux, git, kitty and an agent CLI → [Installation](docs/installation.md).

```bash
git clone https://github.com/GulDmitry/jagt.git && cd jagt
cp jagt.yml.dist jagt.yml           # fill in ONE project: path, baseBranch (+ deployBranch to enable deploy)
cd orchestrator-backend
./gradlew build stageJar
java -jar build/libs/jagt-run.jar
```

Open **http://localhost:8290**, type a ticket key or URL in the first field, press **Start**.

That is the whole of `jagt.yml`; every other key has a default. If anything is missing, jagt refuses to start
and prints the **whole** list at once, each line naming the key that fixes it.

> [!IMPORTANT]
> Run the **staged** jar (`jagt-run.jar`), never `jagt.jar` —
> [why](docs/troubleshooting.md#startup-and-the-jar).

## Commands

| command | what it does |
|---------|--------------|
| `do ABC-42` | read the ticket, cut a worktree, launch an agent |
| `ide ABC-42` | open the worktree in your editor — the live diff |
| `focus ABC-42` | jump into the agent's session and talk to it |
| `ship ABC-42` | commit, push, open or update the review request |
| `sweep ABC-42` | pull checks + comments; the agent fixes locally and drafts replies |
| `replies ABC-42` | read those drafted replies before they go out |
| `deploy ABC-42` | merge the task branch into the deploy branch |
| `done ABC-42` | close the task and clean everything up |

Those eight come first; `revert`, `respawn`, `resume`, `diff`, `stats`, `activity`, `jobs` and `help` are in
the [full usage guide](docs/usage.md).

Every one is also a button on the board, and `Help` there explains its colours and marks. Free text works too
(`⌘K`) — a model maps it onto exactly one of these commands, which runs through the same gate the button uses.

## Your three checkpoints

jagt waits for you at three points, two of them hard. Nothing between them reaches the outside world.

| checkpoint | when | you run |
|------------|------|---------|
| **Review** | the agent is done, and after every review round | `ide`, then `ship` |
| **CI** | after `ship` | `sweep` — or let jagt poll for you (it only reads and drafts) |
| **Close** | checks green, reviewers satisfied | `done` |

## What you own

- The base branch is **read-only**. Nothing in jagt ever pushes to it.
- The only writes to a shared branch are `deploy` and its undo `revert` — both yours to trigger, never
  automatic, and `revert` adds a commit rather than rewriting history.
- An agent acts on **its own task only**, enforced by the server rather than by a prompt.
- Commands are parsed and run in plain Java, so a button or a typed line spends nothing. **Four things cost a
  model call**: reading a ticket, reading a merge request to `resume` it, reading a review round, and a `⌘K`
  sentence.
- The board listens on `127.0.0.1`: it asks for no password and can deploy, so it stays on loopback until you
  decide otherwise.
- **Nothing is written into your project's tracked files** — no git hook, no config write, nothing to
  uninstall. jagt's own `pre-push` guard lives in the worktree it cut and is reached by that agent's session
  alone; your project's hooks go on running inside it.
- Agents live in tmux and the whole state is one JSON file, so restarting the backend loses nothing.

What it does **not** do: review the code for you, run your CI, hold a tracker or code-host credential, or run
on Windows.

## Swapping a vendor

Agent CLI, terminal, editor and notifier are each an interface with one implementation per vendor — Claude Code
or Codex, kitty, IntelliJ, a desktop notifier. The tracker and the code host are not jagt's at all: they are
whatever **your own** MCP servers reach. Adding one means implementing an interface and naming it in config,
never editing the task flow. → [Configuration](docs/configuration.md)

## Documentation

| | |
|---|---|
| [Installation](docs/installation.md) | prerequisites for macOS and Linux, first-run setup |
| [Usage](docs/usage.md) | the board, every command, the review loop |
| [Configuration](docs/configuration.md) | where a setting goes, and every key there is |
| [Troubleshooting](docs/troubleshooting.md) | symptom → cause → fix |
| [Development](docs/development.md) | test suites, CI, running the Linux suite from a Mac |
| [Architecture](ARCHITECTURE.md) | the code map: what kinds of thing jagt has, and where a new one goes |
| [Use cases](USE-CASES.md) | one-line answers to specific situations |

## License

[Apache 2.0](LICENSE)
