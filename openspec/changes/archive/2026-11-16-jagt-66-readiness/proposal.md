## Why

A pod's probe on `/login.html` proves only that the web server listens, so a platform routed traffic to jagt before its start finished or its clones were done.

## What Changes

- `/actuator/health/readiness` answers 200 only once the start's checks passed and no project clone runs, else 503.
- It is a public path: asked with no login, from the pod's own address.
- `kubernetes.yml.dist` probes it.

## Impact

- `readiness`: a new capability, "Pod readiness".
