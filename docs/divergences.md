# CI divergences

[← ci](rules/ci.md#the-divergence-rule) · [testing](rules/testing.md)

A CI red on a tree the local gate recorded green is a defect in the local run. Each gets one row the day it happens.
`./gradlew divergenceRegister` (from `orchestrator-backend`, needs `gh`) names each red run of a recorded-green tree without a row and fails.

| date | run | tree | class | fix |
|------|-----|------|-------|-----|
