## Why
No line-coverage gate and no vulnerability scan existed, and nothing listed the gates a service must have, so an absent gate was invisible.

## What Changes
- The engineering capability states each Java gate as one requirement, the board's JS beside it.
- A test fails naming a gate its build no longer wires; osv-scanner on the release SBOM fails CI on a known vulnerability.
