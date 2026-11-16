## Why
An organisation runs the Kubernetes example in-house: it needs a liveness probe, a JVM sized for a pod that also holds agent CLIs and a verify run, and manifests passing the restricted Pod Security Standard.

## What Changes
- `/actuator/health/liveness` is a public path, answered with no login.
- The image starts jagt's JVM with `JAGT_JAVA_OPTIONS`, set in the image and overridable by the environment.
- `kubernetes.yml.dist` gains probes, a security context, a service account without a token, resources and an ingress and egress `NetworkPolicy`.
