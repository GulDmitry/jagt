# CI divergences

[← ci](rules/ci.md#the-divergence-rule) · [testing](rules/testing.md)

A CI red on a tree the local gate recorded green is a defect in the local run. Each gets one row the day it happens.
`./gradlew divergenceRegister` (from `orchestrator-backend`, needs `gh`) names each red run of a recorded-green tree without a row and fails.

| date | run | tree | class | fix |
|------|-----|------|-------|-----|
| 2026-11-23 | 45257927292 | 337cf73dd | no twin: the local audit scanned the SBOM, never the base images base-images.yml scans | 05136a0aa |
| 2026-11-23 | 45209331215 | 7d6ca72b8 | port race: freePort() probed then released a port compose bound later; local ran fewer stand runs at once | cda678495 |
