## Why
`kubernetes.yml.dist` sets `JAGT_PUBLIC_URL` to the ingress host, but `LoopbackFilter` refused every Host and Origin outside loopback and `auth.allowedHosts`: the example pod answered 403 behind its ingress.

## What Changes
- The host of `server.publicUrl` passes `LoopbackFilter` as an `auth.allowedHosts` entry does; `allowedHosts` stays for further names.
- `auth: none` with a `server.publicUrl` naming a non-loopback host refuses the start, as `allowedHosts` does.
