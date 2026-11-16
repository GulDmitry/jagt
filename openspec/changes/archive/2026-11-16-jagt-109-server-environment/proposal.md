## Why
A server session checks its work with a project's toolchain, and the image jagt ships holds none. Where the toolchain comes from, and what checks a change after deploy, was written nowhere.

## What Changes
- New capability `server-environment`: the image is a base, an organisation's image adds toolchains, sessions check in it; acceptance is the outer loop.
- `jagt.server.yml.dist` enables the acceptance role.
