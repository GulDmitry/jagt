# jagt — TODO

## Composition checks nobody can write yet (open)

- every (status × capability × outcome) decided — needs a capability to declare which outcomes it can return.
- a job's declared capability and watched statuses exist — needs `Job` to declare what it needs.
- a required port capability present in the SELECTED adapter — needs ports to declare their capabilities
  (`Viewer.supports(TAB_TITLES)`, `CodeHost.supports(RESOLVES_THREADS)`), which is also what would let jagt refuse
  auto-review on a host that cannot report thread resolution instead of relaying the same comments forever.

Each of the three is a small interface change plus a check. None of them is worth doing before something needs it.

## Move the code host's credentials into the orchestrator (open, conceptual)

Today jagt holds a token only when `orchestrator.code-host.*` / `tracker.*` are set; everything else reaches a
host through the AGENT's own MCP session, i.e. the human's credentials. Making jagt the credential holder is what
would let it do the outside reads and writes itself, deterministically, with no model in the path.

- For: `ship`, a review round and a resume stop depending on an agent session and on a paid read. One place to
  configure, one place to fail, and every failure names a key instead of a lost round.
- Against: jagt becomes a secret store. The board listens on loopback WITHOUT auth and can already deploy — with
  credentials behind it, whoever reaches that port acts as the human on the host. `AGENTS.md` states the current
  property plainly ("with neither configured the backend holds no credential at all"), and that sentence is what
  would have to go.
- So the question is not the wiring (it exists) but the guarantee: what jagt must promise before it is allowed to
  keep a token — where it is read from, what may act with it, and what the board needs before it may.
