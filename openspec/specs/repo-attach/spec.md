# repo-attach Specification

## Purpose

A running task gains a repository, one a session asked for or one the human names.

## Requirements

### Requirement: A running task gains a repository
`add-repo` SHALL cut the task branch's worktree in the project the request names, as `do` does (`NewTaskWorktrees`),
or adopt one already on that branch (`GitWorktrees.worktreeOf`). It SHALL register it after the session's own and rewrite
every worktree's briefing and sandbox. It SHALL clear the request, hand the session the path and move the task to `IN_PROGRESS`.
A cut that fails SHALL unwind and leave the request standing, the cause on the card.
`add-repo <task> <project>` SHALL record that project as the request, then run as above. The session need not have asked.
It SHALL be refused, nothing changed, for a project not in `jagt.yml`, or one the task already works in.
It SHALL be refused at `PLAN_PENDING`, `DEPLOYED`, `DEPLOY_CONFLICT`, `REVERTED` and `DONE`.

#### Scenario: The request is granted
- **WHEN** you press `add-repo` on ABC-42, which asked for `schemaRegistry`
- **THEN** its worktree exists, the session is told where, the request is gone and the task is `IN_PROGRESS`
- **AND** a status only a human leaves keeps its own

#### Scenario: The cut fails
- **WHEN** git refuses the worktree
- **THEN** nothing of it is left, the request stands, and the card reads NEEDS INPUT with the cause

#### Scenario: A worktree already stands on the branch
- **WHEN** ABC-42's branch is checked out in a `schemaRegistry` worktree jagt did not cut
- **THEN** `add-repo` registers that worktree, its uncommitted work untouched, and a failure leaves it in place

#### Scenario: The session's repository ships its own memory file
- **WHEN** ABC-42's first repository ships `CLAUDE.md`, so jagt briefed it in `CLAUDE.local.md`
- **THEN** `add-repo` writes that briefing again in `CLAUDE.local.md` and attaches

#### Scenario: The attached repository is trusted as it stands
- **WHEN** `add-repo` attaches `schemaRegistry` to ABC-42
- **THEN** its git config and hooks join the trusted record as they are, and the next `ship` is not refused for them

#### Scenario: The human names the project
- **WHEN** you type `add-repo ABC-42 schemaRegistry` on ABC-42 in `REVIEW_PENDING`, which asked for nothing
- **THEN** its worktree exists, the session is told where, and the task is `IN_PROGRESS`

#### Scenario: A named project is refused
- **WHEN** you name a project not in `jagt.yml`, one ABC-42 already works in, or ABC-42 is `DEPLOYED`
- **THEN** the press is refused with the reason, and the task, its request and its worktrees are as they were

### Requirement: A running task loses a repository
`remove-repo <task> <project>` SHALL close that repository's open request on its code host, then take the repository out of
the task as one the host closed leaves: its checkout goes, its branch stays, the session is told. A repository with no
request just leaves. The status does not change. A close that fails SHALL leave the repository on the task and report the cause.
It SHALL be human-only and typed with a project, offered only where the task holds several repositories.

#### Scenario: A repository with a request is removed
- **WHEN** you type `remove-repo ABC-42 schemaRegistry` on ABC-42, which works in `api` and `schemaRegistry`
- **THEN** the request of `schemaRegistry` is closed on its host, the repository leaves ABC-42 and the session is told
- **AND** ABC-42 keeps its status

#### Scenario: A repository with no request is removed
- **WHEN** `schemaRegistry` has no request on ABC-42
- **THEN** it leaves with no call to a host

#### Scenario: The host refuses the close
- **WHEN** the host refuses to close the request
- **THEN** `schemaRegistry` stays on ABC-42 and the refusal carries the host's own words

#### Scenario: Nothing to remove
- **WHEN** ABC-42 works in one repository
- **THEN** `remove-repo` is not offered

### Requirement: A removal that strands the task is refused
`remove-repo` SHALL be refused, nothing changed, for a project not on the task and for the task's primary repository.
It SHALL be refused for a repository already deployed (`revert` first).
It SHALL be refused at `NEW`, `DEPLOY_CONFLICT` and `DONE`.

#### Scenario: A removal is refused
- **WHEN** you name a project not on ABC-42, its primary repository, or one ABC-42 has deployed
- **THEN** the press is refused with the reason, and the task, its requests and its worktrees are as they were

### Requirement: A host closes a request through its driver
The code-host seam SHALL close a request as it reads a pipeline: `github` runs `gh pr close <url>`, `gitlab` runs
`glab mr close <iid> -R <repository>`, `command` runs the project's `closeCommand` with `{request}` replaced by the request's
address, `none` and `replay` close nothing and refuse. An unset `closeCommand` SHALL refuse naming the key.

#### Scenario: A GitHub request
- **WHEN** the request is `https://github.com/acme/shop/pull/7`
- **THEN** jagt runs `gh pr close https://github.com/acme/shop/pull/7`

#### Scenario: A GitLab request
- **WHEN** the request is `https://git.example.com/acme/shop/-/merge_requests/7`
- **THEN** jagt runs `glab mr close 7 -R git.example.com/acme/shop`
