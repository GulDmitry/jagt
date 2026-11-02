## ADDED Requirements

### Requirement: A project names the remote its branches are shared through
`projects.<key>.remote` SHALL name the git remote every fetch, push and remote-tracking ref of that repository uses, `origin` where unset.
`none` SHALL mean a repository kept on this machine: worktrees are cut from the local base branch, nothing is fetched or pushed, and a
deploy merges into the local deploy branch. Startup MUST refuse a remote `git remote` does not list, and `none` beside a `codeHost`
that is not `none`, each naming its keys; it reads no network.

#### Scenario: A repository kept on this machine
- **WHEN** `remote` is `none` and ABC-42 starts
- **THEN** its worktree is cut from the local `main` and no fetch runs

#### Scenario: A remote the repository lacks
- **WHEN** `remote` is `upstream` and `git remote` lists `origin` alone
- **THEN** startup refuses, naming `orchestrator.projects.<key>.remote`
