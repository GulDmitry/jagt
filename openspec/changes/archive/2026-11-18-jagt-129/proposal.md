## Why
`KindEngine.close()` ignored the result of `kind delete cluster`; the run root was deleted and the cluster name freed while the cluster lived, and the next run failed creating it.

## What Changes
- `close()` fails naming the cluster and the exit when the delete fails or passes its limit, so the run root stays and the next run start's cleanup deletes the cluster.
