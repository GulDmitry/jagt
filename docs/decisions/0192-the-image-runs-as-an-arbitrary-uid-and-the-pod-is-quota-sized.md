# 0192 — The image runs as an arbitrary UID and the pod is quota-sized

[← decisions](README.md) · amends [0171](0171-the-image-is-a-base-without-agent-clis.md) and
[0179](0179-the-kubernetes-pod-is-restricted-and-its-jvm-sized.md)

## Status

Accepted 2026-11-17 by the owner's order: the first Kubernetes run is an OpenShift namespace; only what is stable there.

## Context

- Measured read-only on that cluster 2026-11-17: pods are admitted under `restricted-v2` with a UID from the namespace range;
  a fixed `runAsUser` or `fsGroup` is refused.
- Its quota is shared (23.1 of 30 Gi memory requested); a container without a CPU limit gets the LimitRange's 600m.
- jagt alone measured 260-306 MB RSS with an 80 MB heap; the rest is agent CLIs and one verify run.
- The acceptance role's Chromium runs in jagt's own container, and Playwright's browsers need glibc.

## Decision

- **The image runs as any UID in group 0**: `USER 10001:0`; `/app`, `/data` and the home folder are group 0 and group-writable
  (`g=u`), `HOME` is set. A plain cluster may set its own `runAsUser` in the manifest.
- **`examples/kubernetes.yml` fixes no UID, GID or `fsGroup`**; `runAsNonRoot`, seccomp and dropped capabilities stay.
- **Memory request 2Gi, limit 4Gi, an explicit CPU limit, ephemeral storage requested and limited, `/tmp` an `emptyDir` of
  256Mi.** These are estimates, to measure.
- **The heap is fixed: `-Xmx512m`** in the image's `JAGT_JAVA_OPTIONS`, replacing a quarter of the limit.
- **`maxTasks: 4` in `jagt.server.yml.dist`**, so each task's agent CLI fits the limit (an estimate).
- **The runtime base stays the glibc Temurin JRE.**

## Rejected

- Alpine: Playwright's Chromium needs glibc.
- A browser sidecar: a second container and a second memory request on a tight quota.
- A CPU request without a limit: the LimitRange then sets 600m and throttles a verify run.

## Reopen when

- A measured memory figure beats these estimates, or the cluster's quota or SCC changes.
