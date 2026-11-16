## Why
A dependency, action, base image or wrapper could change without a diff: nothing pinned them.

## What Changes
- Every Gradle project and the plugin classpaths lock their dependencies in a committed lockfile.
- The wrapper carries its checksum; every action is a commit sha, every base image a digest.
- Renovate opens the update requests.
