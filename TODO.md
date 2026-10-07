# jagt — TODO

## The jar runs only inside a clone of jagt (open)

`OrchestratorPaths.findRoot` walks up for `jagt.yml.dist` or `mcp_client.js`, and `AbstractAgentRuntime`
symlinks that bridge from disk, so a released jar starts nowhere else. Plan: both as classpath resources, root
falling back to the launch directory, written there on first run.

Decide first: what `root` means once nothing marks it, since worktrees are cut beside it.

## jagt reads the code host and the tracker itself (concept, someday)

Before holding a token, jagt promises: where it is read from, what may act with it, what the board
needs first — loopback, no auth, able to deploy. [`docs/rules/seams.md`](docs/rules/seams.md).

## One task's work split into reviewable requests (idea)

A large ticket lands as one unreviewable request; a splitter would cut it into 300–400 changed-line requests.

Decide first: where a cut may fall (only a commit boundary stays buildable), how each targets the
previous, and the fate of comments a later cut superseded.

## Read the Master's record back (open)

After ~10 finished tasks under the default `judge`, read the `master` report: where its verdicts and yours
differed reopens [0018](docs/decisions/0018-the-master-judges-by-default.md) or the brief.
