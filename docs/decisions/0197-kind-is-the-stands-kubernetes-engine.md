# 0197 — kind is the stand's Kubernetes engine

[← decisions](README.md) · on [0174](0174-the-stand-runs-in-jagts-container.md),
[0179](0179-the-kubernetes-pod-is-restricted-and-its-jvm-sized.md),
[0192](0192-the-image-runs-as-an-arbitrary-uid-and-the-pod-is-quota-sized.md) ·
rule: [`stand-engines`](../../openspec/specs/stand-engines/spec.md)

## Status

Accepted 2026-11-17 by the owner's order: restarts tested in docker and Kubernetes, in one suite, not Testcontainers.

## Context

- `containerTest` drove the image under compose with Testcontainers beside `standTest`: two suites, two harnesses, no Kubernetes.
- `examples/kubernetes.yml` was applied by nobody before a release.

## Decision

- **The engine is a stand seam**: `-Pengine=host|compose|kind`, default `compose`;
  `JagtProcess`, `JagtContainer` and `KindEngine` implement `JagtEngine`.
- **`containerTest` folds into `standTest`** as server scenarios over the shipped `jagt.server.yml.dist`; its source set and Testcontainers go.
- **kind builds one cluster per server run**, named after the run's root, its kubeconfig in the root, deleted at the end even on failure
  (the marker names it, so `standCleanup` deletes a dead run's cluster).
- **The image is built once** by `standImage` and loaded with `kind load docker-image`; `examples/kubernetes.yml` is applied as shipped:
  its image, replica count and Secret keys are the only edits, each failing loudly if the file drifts.
- **Fixtures reach the volume through a seed pod** (`kubectl cp`) before the jagt pod starts: the first clone runs inside startup.
- **The board is reached by `kubectl port-forward`**, opened again after every pod replacement.
- **A restart is an engine call and a world event**: `JVM` kills the process,
  `UNIT` replaces the container or pod, `UPGRADE` starts a new image on the old volume.
- **A scenario that reads the run's root is skipped on kind**: the pod mounts a volume, not the stand's root.
- **`HOME` is `/data/home`**, so a revived session's `--continue` finds its conversation and login on the volume.
- **CI**: one `stand` job, `strategy.matrix.engine`: `compose` per push and pull request, `kind` nightly;
  kind needs a privileged runner, so GitLab runs compose alone.
- **Ceilings** ([0176](0176-every-kind-has-a-ceiling.md)): `ciJobs` 8 for the `stand` job on both hosts, `specCapabilities` 49, `sourceSets` 26.

## Rejected

- Testcontainers: a second harness beside the stand, with no Kubernetes.
- Chainsaw or KUTTL: a second test language for what Java scenarios already say.
- One shared cluster per Gradle run: a pod delete or an upgrade in one scenario would reach every other.
- A hostPath mount of the run's root into the pod: the manifest under test would no longer be the shipped one.

## Reopen when

- A kind run costs more than ten minutes on the nightly runner, or the cluster's start dominates the suite.
- A Kubernetes cluster is available in CI without privilege, so GitLab can run it.
