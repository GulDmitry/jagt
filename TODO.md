# jagt — TODO

## The jar runs only inside a clone of jagt (open)

`OrchestratorPaths.findRoot` walks up for `jagt.yml.dist` or `mcp_client.js`, and `AbstractAgentRuntime`
symlinks that bridge into each worktree from disk — both are repository files, so a released jar starts nowhere
else.

The plan: ship both as classpath resources, fall back to the launch directory as root, and write them there on
first run.

What to decide first: what `root` means once nothing marks it, since worktrees are cut beside it.

## An orchestrator that reads the code host and the tracker itself (concept, someday)

What jagt must promise before it may hold a token — where it is read from, what may act with it, and what the
board needs first, listening on loopback without auth and able to deploy. As it stands:
[`docs/rules/seams.md`](docs/rules/seams.md).

## Make the small reads thin (open)

A read that fetches one ticket loads every MCP server the human's config carries, which is what makes the
cheapest call in jagt cost ~25k tokens before it reads anything.

What to decide first: `orchestrator.assistant.mcp-config` pins one server list for every call, so the open part
is a list per kind of read (`AssistantCallKind` names them) and what a kind without one falls back to.

## Split one task's work into review requests a human can actually read (idea)

A large ticket lands as one request, and a request nobody can read is a request nobody reviews. A splitter
would send the same work out as several, cut at a configured size — around 300–400 changed lines each.

What to decide first: where a cut may fall (only a commit boundary leaves each request buildable), how they are
chained so the second targets the first, and what happens to comments a later cut has superseded.

## Keep a task's artifacts after `done` (idea)

The far end of [the artifact chain](ARCHITECTURE.md#the-artifact-chain): `done` deletes the briefing, the
standing instruction and the drafted replies, while the branch and the request outlive the task.

What to decide first: whether an artifact outliving the worktree lives in git (whose, and on which branch — the
base branch is read-only) or beside `state.json`, and what the board shows for a task that is gone.

## A card that keeps a red run visible through a sweep that could not read it (open)

A task carries ONE checks word, so a round whose pipeline listing failed writes `unknown` over a `failed` still
true, while carrying the old word forward would advance a task on a green nobody looked at.

What to decide first: whether "what was last READ" and "whether the latest round could read it" earn two fields
on `TaskState`, and what mark keeps a stale verdict from reading as a current one.

## `TmuxSessionHost` has no test beside it (open)

The sole `SessionHost` implementation and the only adapter class with nothing testing it: a real tmux is the
only honest test, so the hermetic suite cannot cover it.

What to decide first: whether it belongs in `e2eTest`, which already has tmux, or in a suite of its own.

## A flag for the tasks that matter, on the card (idea)

An inbox flag, top-right: the few tasks you must not lose sit in a board where every card reads equally urgent.

What to decide first: `card-top` already ends in the attention badge, so a flag there says what it replaces —
and no colour is free, `--danger` meaning broken and `--you` your move. Likely a shape, sorted first not tinted.
