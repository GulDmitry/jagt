# 0179 — The Kubernetes pod is restricted and its JVM sized

[← decisions](README.md) · on [0172](0172-readiness-is-spring-boot-actuators-probe.md) ·
rule: [`server-environment`](../../openspec/specs/server-environment/spec.md)

## Status

Accepted 2026-11-16 by the owner's order: the manifests are what an organisation runs in-house.
Amended by [0192](0192-the-image-runs-as-an-arbitrary-uid-and-the-pod-is-quota-sized.md): no fixed UID, a fixed heap, quota-sized resources.

## Context

- One pod holds jagt's JVM, tmux, agent CLIs and one verify run (verify runs go one at a time).
- A JVM sizes its heap from the container's memory; the default is a quarter of it. Spring Boot's graceful shutdown waits 20 s
  per phase and `ShutdownEndings` is a second phase ([0169](0169-stop-work-is-a-smartlifecycle-after-the-web-server.md)).

## Decision

- **Options in `JAGT_JAVA_OPTIONS`, read by `entrypoint.sh` for jagt's JVM alone**: `JAVA_TOOL_OPTIONS` and `JDK_JAVA_OPTIONS`
  reach every JVM a session starts (a build, a language server) and would cap their heaps too.
- **A quarter of the memory for the heap**, the rest for the agent CLIs and the verify run; serial GC: a board needs no pause
  target, and it adds no collector threads beside a verify run; exit on out-of-memory so the restart policy acts, a heap dump
  on the volume first.
- **One flat `examples/kubernetes.yml`**: one copied file and one command; an organisation lists it under a kustomization's
  `resources:` when it wants an overlay.
- **Restricted Pod Security Standard**: non-root fixed UID and GID, no privilege escalation, `drop: [ALL]`, seccomp
  `RuntimeDefault`, no service-account token. The root filesystem stays writable: sessions install toolchains under HOME;
  `/tmp` is an `emptyDir`.
- **Memory requested as limited, no CPU limit**: memory cannot be reclaimed, and a CPU limit throttles a verify run. The sizes are
  estimates, to measure.
- **Probes**: startup and liveness on `/actuator/health/liveness` (the application's own state, no outside dependency), readiness
  as in 0172. `terminationGracePeriodSeconds: 60` is preStop 5 s plus two 20 s phases plus headroom.
- **`NetworkPolicy`** denies ingress but the controller's namespace, and limits egress to DNS and HTTPS and SSH on public addresses.
- **No `PodDisruptionBudget`**: with one replica, `minAvailable: 1` blocks every node drain and `maxUnavailable: 1` allows what
  happens anyway.

## Rejected

- Kustomize base and overlay: three files and a directory for what one file does.
- An AOT cache or layered jars: not measured cheaper; the start is one per pod life.
- A read-only root filesystem: breaks toolchain installs of sessions.
- G1: more threads and native memory for a pause nobody feels.

## Reopen when

- A measured start or memory figure beats these defaults, or a read-only root becomes possible for sessions.

Sources checked 2026-11-16 (pages read in part): Spring Boot 4.1.2 reference, Actuator "Kubernetes Probes" and "Graceful Shutdown";
Kubernetes "Pod Security Standards", Restricted profile; Oracle `java` command, Java 25 (container support, heap dump and
`JDK_JAVA_OPTIONS`). `ExitOnOutOfMemoryError`, `MaxRAMPercentage` and the GC choice were checked by running the image.
