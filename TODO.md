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

## Split one task's work into review requests a human can actually read (idea)

A large ticket lands as one request, and a request nobody can read is a request nobody reviews. A splitter
would send the same work out as several, cut at a configured size — around 300–400 changed lines each.

What to decide first: where a cut may fall (only a commit boundary leaves each request buildable), how they are
chained so the second targets the first, and what happens to comments a later cut has superseded.

## A flag for the tasks that matter, on the card (idea)

An inbox flag, top-right: the few tasks you must not lose sit in a board where every card reads equally urgent.

What to decide first: `card-top` already ends in the attention badge, so a flag there says what it replaces —
and no colour is free, `--danger` meaning broken and `--you` your move. Likely a shape, sorted first not tinted.

## One declared language for everything that crosses into jagt (concept)

Every message between a session, the orchestrator and the Master speaks one protocol: a folder of schemas with
the validation rules beside them, covering a ticket read, a `resume`, a push, what the hooks report, and a
status report. Today the shapes exist but are scattered — the MCP tool declarations are inline strings in
`surface/mcp/tools/`, the paid reads carry `--json-schema` blocks in `adapter/assistant/`, and the consistency
rules live in prompt prose. `protocol/AgentStatusMessage` is the first one gathered.

A schema alone cannot carry half of it: "in this enum" and "required" it expresses, "`reviewRequests` instead of
`reviewRequestUrl`, never beside it" and "a listing you could not get is `pipelineStatus=unknown` with an empty
`failure`" it does not. Both halves belong in the same place.

What to decide first: who writes a schema and at what level, what language the consistency rules are written in,
and whether the module stands alone — generated from, or generating, the MCP declarations — or stays part of the
application. Then tie each message to the artifact it produces.

## Run the Master session against real work before anyone turns it on (idea)

It ships off, marked experimental, and stays off until it has been driven by hand against live tasks: what it
judged, where its verdict and yours differed, what it cost per wake. [`docs/roadmap.md`](docs/roadmap.md) has
the shape; the finished-task record is what the comparison is read from.

What to decide first: what a trial run is allowed to touch — reading and writing a file only, with no verb
issued, is the answer unless something says otherwise.
