## Why

A kind of move addressed to no region passed the class check, so a kind forgotten for a whole region was never caught.

## What Changes

- Building a chart that declares its moves fails on a kind addressed to no region and owned by no role.
- A role-owned kind is declared so; it vanishes with its role.
- A round read lands as `Landed` only where the chart answers it, never as the status it met.

## Capabilities

### Modified Capabilities

- `statechart`: a kind with no region and no owner refuses the build.
