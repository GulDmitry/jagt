## Why
Behind an ingress the request's own base URL is the pod's, not the address the provider must return to.

## What Changes
- The redirect URI sent to the provider is `server.publicUrl` plus `/login/oauth2/code/oidc`.
