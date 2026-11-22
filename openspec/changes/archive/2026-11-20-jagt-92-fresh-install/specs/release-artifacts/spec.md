## MODIFIED Requirements

### Requirement: CI proves the installers end to end
CI SHALL run an install check on Linux, macOS and Windows runners before a release is published.
The check serves a fake feed on localhost and installs `0.0.1` with the platform's installer.
It starts the launcher with `orchestrator.releaseFeed.url` at that feed and runs `upgrade` once `0.0.2` is kept.
It copies no file from the repository and writes no `jagt.yml` and no project: the install is as a user has it.
It fails naming the stage that failed, and every wait is bounded.
#### Scenario: Install, upgrade and restart
- **WHEN** the install check runs on a runner
- **THEN** `/state` answers under `0.0.1`, `versions/0.0.2/jagt.jar` is kept, `upgrade` makes `current` name `0.0.2`
- **AND** the launcher restarts jagt, `/state` answers again and `upgrade` is no longer offered

#### Scenario: A runtime checksum beside a redirected download
- **WHEN** the runtime feed redirects the download to a signed storage URL
- **THEN** the installer reads the runtime's checksum beside the feed's first redirect target

## ADDED Requirements

### Requirement: An installed jar starts with no checkout and no project
A jar run from `<home>/versions/<v>/` SHALL take `<home>` as its root, and `data/` under it, when no root is named.
It SHALL write the files it carries into the root at every start: each role's `.dist` files, `jagt.desktop.yml.dist`, `mcp_client.js`.
It SHALL never touch a file of the human's, `jagt.yml` included.
Without a `jagt.yml` it SHALL run on the packaged defaults, and with no project it SHALL start.
Adding the first project SHALL create `jagt.yml`.
#### Scenario: A fresh install starts
- **WHEN** a jar installed under `<home>/versions/<v>/` starts with no `jagt.yml` and no checkout beside it
- **THEN** the roles' `.dist` files, `jagt.desktop.yml.dist` and `mcp_client.js` are in `<home>`, and `/state` answers
#### Scenario: An upgrade brings new shipped text
- **WHEN** a newer jar starts over a `<home>` holding an older `roles/session/brief.md.dist`
- **THEN** that file holds the newer jar's text
- **AND** `roles/session/brief.md`, if the human wrote one, is as it was
#### Scenario: A root is named
- **WHEN** `ORCHESTRATOR_ROOT` or `--orchestrator.root` names a root
- **THEN** nothing is written into it, and a checkout's files are never replaced by a jar's
#### Scenario: The first project of a fresh install
- **WHEN** the human adds a project and `jagt.yml` does not exist
- **THEN** `jagt.yml` is created holding that project under `orchestrator.projects`
