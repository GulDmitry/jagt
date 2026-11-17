## Why
Restarts of jagt's image are tested under docker and Kubernetes, in one suite: `standTest` on a chosen engine, not a second source set.

## What Changes
- The stand's engine is a seam chosen by `-Pengine=host|compose|kind`; `-Plocal` goes.
- `containerTest` folds into `standTest` as server scenarios; its source set and Testcontainers go.
- A restart is a stand event of three kinds: the JVM, the container or pod, the image on the old volume.
- The image's `HOME` is `/data/home`, on the volume.
