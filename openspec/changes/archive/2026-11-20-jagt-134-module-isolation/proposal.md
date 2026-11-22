## Why
The rings rule pins the direction between Gradle modules, not that a module is reached only through its API, so a leaked abstraction goes unseen.

## What Changes
- `project.modules` in `fitness.yml` declares each Gradle module with the packages others may reference.
- A declared fitness function `measure: module-isolation` counts every reference from one module to a package another does not expose.
- A module declared without `exposes` fails the load.
