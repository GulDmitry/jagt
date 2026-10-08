## Why

`ToolGate` refused only a non-GET `gh`/`glab api`: `gh pr merge 12 --admin`, `glab mr merge 12` and
`gh repo edit --default-branch x` wrote to the code host unrefused.

## What Changes

- A `gh` or `glab` call runs only as a read: `pr`, `mr`, `issue`, `run`, `ci`, `repo` and `auth` reads, and a GET
  `api` with no body. Every other call is refused.

## Capabilities

### Modified Capabilities

- `git`: the host CLIs are an allow-list of reads.
