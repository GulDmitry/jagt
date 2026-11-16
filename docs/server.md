# jagt on a server

[← README](../README.md)

One container, one replica, the board behind a password login. The image holds jagt, git and tmux and no agent CLI
([0171](decisions/0171-the-image-is-a-base-without-agent-clis.md)).

## Bot accounts

One account per service, never your own: a code-host account that clones and pushes task branches, and the agent CLI's
account or API key. A leaked key then costs one service, and revoking it stops nothing else.

## Configuration

```sh
cp jagt.server.yml.dist jagt.yml          # the image ships it as /app/jagt.yml; edit your copy to name your projects
```

- `jagt.server.yml.dist` already opens no terminal window, needs no kitty, notifier, editor or keep-awake, and logs to the console.
- Each key is described in `jagt.desktop.yml.dist` under the same name, and nowhere else.
- One file per environment: keep `jagt.stage.yml` and `jagt.prod.yml` and set `ORCHESTRATOR_CONFIG_FILE=/etc/jagt/jagt.prod.yml`;
  jagt reads that file alone, no profile or overlay involved.
- `agent.sandbox.enabled` stays `false` in a pod: the container is the fence.
- `usageLimits.enabled` stays `false` with an API key: the limits are read from an agent CLI's own login, which a key lacks.
- Behind an ingress, add its host name to `auth.allowedHosts`; any other `Host` or `Origin` is refused with 403.

## Secrets

`.env` for compose, a `Secret` for Kubernetes, the same keys (`.env.dist`), never in `jagt.yml` or an image:

```sh
cp .env.dist .env    # JAGT_PASSWORD_HASH='<bcrypt hash>' (single quotes: compose reads a bare $), JAGT_PUBLIC_URL
```

Add the agent CLI's API key and the code host's token to the same file or Secret, under the names those tools read.

## Git reaches the code host

- **SSH**: mount the bot's private key (mode 0600) and a `known_hosts` into `/home/jagt/.ssh`; the image has `openssh-client`.
  Without `known_hosts` the first clone fails on host verification: jagt never answers that prompt.
- **Token**: a credential helper in your image reading the token from the environment, with `https://` project urls.
- `codeHost: github` or `gitlab` also needs `gh` or `glab` in your image.

## Build and deploy

Your image sits on jagt's: `Dockerfile.dist` adds the agent CLI, `gh` and `glab` as optional blocks.

```sh
docker build -t jagt-base .                                              # in a jagt checkout
docker build -f Dockerfile.dist --build-arg BASE=jagt-base -t my-jagt .
```

**Compose** builds jagt's own image and mounts one volume, `jagt-data`, at `/data`: state, artifacts, repositories,
worktrees and the CLI login. Without the volume a restart starts empty.

```sh
docker compose up -d --build
curl -s localhost:8290/actuator/health/readiness     # 200 once startup checks are green and clones are done
```

**Kubernetes**: `cp kubernetes.yml.dist kubernetes.yml`, set the image, host and Secret values, `kubectl apply -f kubernetes.yml`.
One replica, `Recreate`, one optional PVC, Secret, Service, Ingress; the probe is `/actuator/health/readiness`.

- The first clone runs inside startup, before readiness, so a pod is never ready ahead of it. A clone retried after a
  failure does not hold the pod: the board's head shows it.

## Open the board

Open `JAGT_PUBLIC_URL` and log in; under compose `http://localhost:8290`.

- `/state` needs a login too; only `/actuator/health/readiness` is public.
- A `curl` from the host needs `-H 'Host: 127.0.0.1:8290'`: `LoopbackFilter` refuses any other `Host`.

## Debug from a local CLI

A running jagt is read, never changed, through `read_record` and `read_transcript` on its MCP server and `GET /api/debug/…`
([debug-reads](../openspec/specs/debug-reads/spec.md#requirement-a-running-jagt-is-read-never-changed-through-its-own-door)).
State and reports: `list_tasks`, `GET /state`.

```sh
kubectl port-forward <pod> 8290:8290
curl -s -H "X-Jagt-Master: $(cat <token file>)" -H 'Content-Type: application/json' localhost:8290/mcp \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"read_record","arguments":{"record":"stops"}}}'
```

- `<token file>` is `master-token` beside `state.json`; point a CLI's MCP config at `http://localhost:8290/mcp` with that header.
- Under `auth.mode: password`, `/api/debug/…` needs the login session; `/mcp` needs the token.
- Records: `backend-log`, `master-log`, `stops`, `stops-reviewed`; under `orchestrator.logging: console` the two logs are what stdout carried.
