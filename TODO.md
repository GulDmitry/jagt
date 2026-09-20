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
standing instruction and the drafted replies, while the branch and the request outlive the task; with no ticket
behind it, the words that started it go too. One record per finished task is what anything built on finished
work reads — `stats` cannot be asked for throughput today — and it is worth holding before anything reads it.

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

## A reviewer session, briefed as the human rather than as the worker (idea)

The human is the first and only reader of the plan and the diff. A second session reads them first, briefed as
the reviewer — the business case, the tester, the architecture, the code. Reading is the cheap half, so it can
run the heavier model.

Every point needing a human is already one list: `Move.ownerOf` → `Owner.YOU`. That verdict plus a setting per
point is what takes one off it — advise, or issue the verb the human would have pressed. The judging points go
first; the authorising ones (`ship`, `deploy`, `revert`, posting a reply) are the invariant.

What to decide first: that invariant — [jagt acts on nothing by itself](AGENTS.md) and [a trigger is
deterministic](docs/rules/review.md), both false the moment a verdict issues a verb, on a board that binds
loopback without auth.

## Taking work off the tracker unasked (idea, blocked)

A job that picks up the next ticket and launches it. Blocked upstream: a ticket carries no project to route on
and no label saying it is ready, and one written without the prompts a session works from produces work nobody
asked for. Tickets written per project and per working prompt come first.

What to decide first: what marks a ticket ready to be taken, and what jagt does with one that is not.
