# 0175 — An organisation builds its toolchains onto jagt's image

[← decisions](README.md) · on [0171](0171-the-image-is-a-base-without-agent-clis.md)
Rule: [`server-environment`](../../openspec/specs/server-environment/spec.md)

## Status

Accepted 2026-11-16 by the owner's order.

## Context

- A session compiles, tests, lints and runs a language server in its worktree; each needs the project's toolchain.
- jagt's image is a small base without toolchains ([0171](0171-the-image-is-a-base-without-agent-clis.md)); projects differ.
- The acceptance role runs against the deployed environment, not the worktree.

## Decision

- An organisation builds its own image `FROM` jagt's, adding its projects' toolchains; `Dockerfile.dist` holds optional blocks.
- Inner loop: the session builds and tests in its own container, in seconds, with the toolchain the repository names.
- mise resolves each repository's `.tool-versions` or `mise.toml` to its Java, Node or PHP version; Playwright and its browsers are a second block.
- Outer loop: the acceptance role, on by default on a server, checks the deployed environment.
- jagt names no language, version or project; the blocks are generic and commented.

## Evidence, checked 2026-11-16

- OpenAI Codex cloud: one universal image, pinned versions, cached setup scripts ([docs](https://developers.openai.com/codex/cloud/environments)).
- Claude Code on the web: a default image with common runtimes, cached setup scripts ([docs](https://code.claude.com/docs/en/claude-code-on-the-web)).
- Cursor cloud agents: a Dockerfile per repository, snapshotted; no Docker-in-Docker by default ([setup](https://cursor.com/docs/cloud-agent/setup)).
- None leaves checking to the pipeline alone.

## Rejected

- Toolchains in jagt's image: every version jagt chose would be wrong for someone, and the image would grow without bound.
- Docker-in-Docker or a mounted docker socket: privileged, and unfriendly to Kubernetes.
- Acceptance as the only loop: minutes per cycle for a compile error.

## Reopen when

- A toolchain is needed by most organisations and mise cannot install it.
- A cluster policy allows an unprivileged container build inside a pod.
