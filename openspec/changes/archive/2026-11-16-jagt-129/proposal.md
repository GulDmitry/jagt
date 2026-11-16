## Why
Behind a TLS ingress the pod sees plain HTTP: its redirects, its login cookie and its peer address were the ingress's, not the client's.

## What Changes
- `jagt.server.yml.dist` sets `server.forward-headers-strategy: native`, a secure session cookie and a 30 minute session timeout.
- `kubernetes.yml.dist`'s Ingress carries a live `tls` block for its host.
