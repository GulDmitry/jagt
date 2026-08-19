# jagt — TODO

## Split `:usecase` from `:adapter` (open, needs one decision)

`:core` is its own module and the compiler now enforces the centre's direction. The next split cycles:
`adapter/Executables` (PATH + the known install directories) is read by `config/` and `startup/ToolchainCheck`,
while `adapter/` reads `config/` 21 times. The way out is to resolve a binary WHERE IT IS SPAWNED instead of when
the config record is built — a change to how binaries are found, which is what broke `ide` on this machine once
before. Decide it deliberately, then the split is mechanical.

## Composition checks nobody can write yet (open)

- every (status × capability × outcome) decided — needs a capability to declare which outcomes it can return.
- a job's declared capability and watched statuses exist — needs `Job` to declare what it needs.
- a required port capability present in the SELECTED adapter — needs ports to declare their capabilities
  (`Viewer.supports(TAB_TITLES)`, `CodeHost.supports(RESOLVES_THREADS)`), which is also what would let jagt refuse
  auto-review on a host that cannot report thread resolution instead of relaying the same comments forever.

Each of the three is a small interface change plus a check. None of them is worth doing before something needs it.
