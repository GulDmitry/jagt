# jagt — TODO

## An orchestrator that reads the code host and the tracker itself (concept, someday)

What jagt must promise before it may hold a token — where it is read from, what may act with it, and what the
board needs first, since it listens on loopback without auth and can already deploy. The decision as it stands:
[`docs/rules/seams.md`](docs/rules/seams.md).

## Make the small reads thin (open)

A read that fetches one ticket loads every MCP server the human's config carries, which is what makes the
cheapest call in jagt cost ~25k tokens before it reads anything.

What to decide first: `orchestrator.assistant.mcp-config` pins one server list for every call, so the open part
is a list per kind of read (`AssistantCallKind` already names the kinds) and what a kind with no list of its own
falls back to.

## Split one task's work into review requests a human can actually read (idea)

A large ticket lands as one branch and one request, and a request nobody can read is a request nobody reviews.
A splitter would send the same work out as several requests, cut at a configured size — around 300–400 changed
lines each.

What to decide first: where a cut may fall (a commit boundary is the only one that leaves each request
buildable), how the requests are chained so the second targets the first rather than the base branch, and what
happens to comments on a request a later cut has superseded.

## Start a task from an accepted plan, and keep its artifacts after `done` (idea)

Both halves are about the ends of [the artifact chain](ARCHITECTURE.md#the-artifact-chain): a task could start
from an accepted plan instead of a ticket read, and `done` deletes every artifact the task left behind.

What to decide first: what makes a plan an *accepted* artifact rather than one more paragraph in
`NewTask.instructions`; whether an artifact that outlives the worktree lives in git (whose, and on which branch
— the base branch is read-only) or beside `state.json`; and what the board shows for a task that is gone.

## A card that keeps a red run visible through a sweep that could not read it (open)

A task carries ONE checks word, so a round whose pipeline listing failed writes `unknown` over a `failed` that
is still true, while carrying the old word forward would advance a task on a green nobody looked at.

What to decide first: whether "what was last READ" and "whether the latest round could read it" are worth two
fields on `TaskState`, and what mark the card gives a stale verdict so it cannot be read as a current one.

## `TmuxSessionHost` has no test beside it (open)

It is the sole `SessionHost` implementation and the only adapter class with nothing testing it, and a real tmux
is the only honest test, so the hermetic suite cannot cover it.

What to decide first: whether it belongs in `e2eTest`, which already has tmux, or in a suite of its own asked
for by name.
