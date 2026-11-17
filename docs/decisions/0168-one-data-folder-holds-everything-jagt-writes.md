# 0168 — One data folder holds everything jagt writes

[← decisions](README.md) · [`startup`](../../openspec/specs/startup/spec.md) · builds on [0021](0021-jagt-runs-from-a-clone-of-its-repository.md)

## Status

Accepted 2026-11-15; the default is superseded by [0190](0190-the-data-folder-is-data-under-the-root.md).

## Context

- State, logs and the master token lived in the launch directory, task worktrees beside each repository.
- A server keeps one volume; a desktop had its writes in as many places as it had repositories.
- A repository's parent had to be writable for every project, and a read-only install could not choose where state goes.

## Decision

- One key, `orchestrator.data`, default `~/.jagt` from `user.home` in `application.yml`; `ORCHESTRATOR_DATA` and
  `JAGT__orchestrator__data` override it.
- It holds `state.json` and its siblings, `worktrees/<project>/<task>`, `repos/<name>` and `logs/`; `ProjectClonesJob` writes `repos/<name>`.
- `jagt.yml` stays outside it: it is the owner's file, read before any data exists.
- A project's `path` is used in place; only a new task's worktree moves under `worktrees/`.
- A task in `state.json` keeps its recorded worktree path; existing worktrees are not moved.
- `DataFolderStart` creates the folder and, once, moves a `state.json` (and its `.bak`) from the root into it.
- `orchestrator.state-file` still wins for the state and its siblings.
- Deploy and revert worktrees are cut at `worktrees/<project>/<task>-deploy` and `-revert`; a repository's parent need not be writable.

## Rejected

- Moving every sibling of the old state: names jagt no longer writes would be listed forever.
- A default in Java and another in `application.yml`: the log paths need it before any bean exists, so `application.yml` owns it;
  a context without it reads the root.

## Reopen when

- A clone is cut anywhere but `OrchestratorPaths.clone`.
