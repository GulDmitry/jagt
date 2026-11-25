# CI divergences

[← ci](rules/ci.md#the-divergence-rule) · [testing](rules/testing.md)

A CI red on a tree the local gate recorded green is a defect in the local run. Each gets one row the day it happens.
`./gradlew divergenceRegister` (from `orchestrator-backend`, needs `gh`) names each red run of a recorded-green tree without a row and fails.

| date | run | tree | class | fix |
|------|-----|------|-------|-----|
| 2026-11-24 | 45407359631 | unknown | cache: the nightly's dependency cache lacked Spotless's detached google-java-format, and the local twin ran offline on a cache it had filled with a narrower resolve; a runner has its network | 0238 |
| 2026-11-24 | 45407359631 | unknown | cache: npm answered ENOTCACHED for the board audit from a cache the twin never filled; a runner reads the registry | 0238 |
| 2026-11-24 | 45338587686 | 82aac1dd5 | network: Maven Central kept answering 429 to the hosted runner past every retry; local resolves from its warm cache | 0235 |
| 2026-11-24 | 45322490446 | 4bf02ab22 | network: the CI image lacked zstd, so the test job missed every cached Gradle home and hit Maven Central's 429; local resolves from its warm cache | 1427c09e6 |
| 2026-11-24 | 45309806524 | c675335ec | state: the local engine volume kept the PowerShell image across runs; a runner's engine starts empty and the stand runs offline | c3b4744e9 |
| 2026-11-24 | 45309806524 | c675335ec | user: the runner's uid 1001 has no account in the image, so the session bus failed; locally uid 1000 had one | c3b4744e9 |
| 2026-11-24 | 45309806524 | c675335ec | host: actions/cache hashed the workspace again after the root container's run and failed; no local twin of the cache step | c3b4744e9 |
| 2026-11-24 | 45300396681 | 1f93d8e26 | user: CI's test container runs as root, home /root, whose -root spelling the home-paths scan matched; local ran as uid 1000 | 6f44c43db |
| 2026-11-24 | 45300396681 | 1f93d8e26 | host: a runner's bind mount keeps uid 1001, the job ran as 1000; Docker on a Mac maps owners, so local never saw it | 14680617d |
| 2026-11-23 | 45257927292 | 337cf73dd | no twin: the local audit scanned the SBOM, never the base images base-images.yml scans | 05136a0aa |
| 2026-11-23 | 45209331215 | 7d6ca72b8 | port race: freePort() probed then released a port compose bound later; local ran fewer stand runs at once | cda678495 |
