# 0171 — jagt's image is a base without agent CLIs

[← decisions](README.md) · supersedes [0021](0021-jagt-runs-from-a-clone-of-its-repository.md) for the image


## Status

Accepted 2026-11-15 by the owner's order: a server runs jagt from a container; builds on [0168](0168-one-data-folder-holds-everything-jagt-writes.md).
Amended by [0192](0192-the-image-runs-as-an-arbitrary-uid-and-the-pod-is-quota-sized.md),
[0199](0199-the-image-layers-a-prebuilt-jar-on-published-base-images.md).

## Context

- [0021](0021-jagt-runs-from-a-clone-of-its-repository.md) installs by cloning; a server has no clone and no desktop.
- `OrchestratorPaths.findRoot` needs `jagt.desktop.yml.dist` or `mcp_client.js` above the working directory, or `ORCHESTRATOR_ROOT`.
- Every write already goes to one folder ([0168](0168-one-data-folder-holds-everything-jagt-writes.md)).

## Decision

- The `Dockerfile` at the repository root builds the jar in a JDK stage and runs it on `eclipse-temurin:25-jre-noble`.
- The root is `/app`: the jar, `mcp_client.js`, `jagt.desktop.yml.dist`, `roles/` and `jagt.server.yml.dist` as `jagt.yml`.
- The data folder is `/data`; mounting a volume there is the user's choice, without one the container works and a new container starts empty.
- The image sets `platform: linux` and `terminal: none`: no terminal window opens, so no kitty is checked for.
- The image holds git, tmux, procps, lsof and openssh-client, and runs as a non-root user.
- No agent CLI, `gh`, `glab` or node: a user's image is `FROM` jagt's and adds what its agent needs.
- `docker-compose.yml` runs the service on 8290 with the named volume; Secrets come from `.env`, copied from `.env.dist`.
- A clone stays how a developer runs jagt; 0021 holds for that.

## Rejected

- An agent CLI in the image: each is a vendor and a version jagt must not choose ([0130](0130-every-third-party-is-a-configured-seam.md)).
- The staged jar: its symlink guards a running desktop JVM, a container has none.
- A `VOLUME` instruction: it makes an anonymous volume that outlives a restart, so "no volume" would never be empty.
- A jar-only image: the bridge and roles are read from disk at the root.

## Reopen when

- `findRoot` no longer needs files on disk.
- A second base image is asked for.
