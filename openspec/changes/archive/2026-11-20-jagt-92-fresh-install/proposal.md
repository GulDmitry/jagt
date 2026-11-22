## Why
A jagt installed by `install.sh` or `install.ps1` refused to start: it needed `roles/`, `jagt.desktop.yml.dist`, a `jagt.yml` and a project, none of which the install carries. The install check hid it by copying them from the repository.

## What Changes
- The jar carries the roles' `.dist` files, `jagt.desktop.yml.dist` and `mcp_client.js`; a jar run from `versions/<v>/` writes them into the install home at every start.
- The install home is the root of such a jar, and `data/` lives under it.
- No `jagt.yml` means the packaged defaults; the board adds a project, creating the file.
- A configuration with no project starts: the "defines no projects" refusal is gone.
- The install check copies nothing from the repository and writes no project.
