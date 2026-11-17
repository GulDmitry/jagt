# jagt on a server

[← README](../README.md)

One container, one replica, the board behind a password login. The image holds jagt, git and tmux and no agent CLI
([0171](decisions/0171-the-image-is-a-base-without-agent-clis.md)).
`tini` is PID 1 and jagt runs under a relaunch loop: a jagt killed by a signal starts again and its sessions' tmux
keeps running; any other exit ends the container; stopping the container stops both.

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
- Verify runs go one task at a time, since the verify job never overlaps itself: size the pod's limits for one
  `verifyCommand` beside the sessions.
- The board's head names a `verifyCommand`, `setupCommand` or acceptance flows program the image lacks:
  extend the image (`Dockerfile.dist` has the Node and Chromium layers).
- The host of `JAGT_PUBLIC_URL` is the one the ingress must forward; any other `Host` or `Origin` is refused with 403.
- The ingress ends TLS (the manifest's `tls` Secret); the starter reads `X-Forwarded-*`, sets the cookie Secure
  and ends an idle login after 30 minutes.
- `X-Forwarded-For` counts only from a private-range proxy; an ingress on a public address is named in
  `server.tomcat.remoteip.internal-proxies`, else the login backoff keys on the ingress, not the client.

## Single sign-on

`auth.mode: oidc` replaces the password with any OpenID Connect provider; one subject or email is let in, any other gets 403.

```yaml
orchestrator:
  auth:
    mode: oidc
    oidc:
      issuerUri: https://sso.example.com/realms/main
      clientId: jagt
      clientSecretEnv: JAGT_OIDC_SECRET   # the NAME of the variable holding the client secret
      allowed: owner@example.com          # the id token's subject or email
```

- Register `<publicUrl>/login/oauth2/code/oidc` as the client's redirect URI; the client asks for `openid email`.
- A missing key, or a `clientSecretEnv` naming an unset variable, refuses the start naming it.
- The provider is discovered on the first login, so one that is down does not stop jagt starting.
- `email` is read from the id token alone; a provider that leaves it out matches on the subject.

## Secrets

`.env` for compose, a `Secret` for Kubernetes, the same keys (`.env.dist`), never in `jagt.yml` or an image:

```sh
cp .env.dist .env    # JAGT_PASSWORD_HASH='<bcrypt hash>' (single quotes: compose reads a bare $), JAGT_PUBLIC_URL
```

Add the agent CLI's API key and the code host's token to the same file or Secret, under the names those tools read.

## The three identities

Each is set by your image's environment or a mounted file, never a value in `jagt.yml` or the image.

| identity | what it is | set by |
|----------|------------|--------|
| tracker | the MCP servers a session and a read reach the tracker through, and the user `take` starts for | a mounted file whose path `JAGT_MCP_CONFIG` holds, `JAGT_TRACKER_USER` |
| code host | the account `gh` or `glab` acts as | `GH_TOKEN` or `GITLAB_TOKEN` (`GITLAB_HOST` for a self-hosted host) in the environment |
| notification | the channel's webhook | the starter's commented `chat` channel: `JAGT_NOTIFY_WEBHOOK` holds the URL, `webhookEnv` names it; unset while a route uses it, the start is refused |

- The mounted MCP file carries the tracker's own `${VAR}` secrets; a `gh` or `glab` login under HOME is only their fallback.
- Pass the variables at run time (`docker run -e`, a compose `.env`, a Kubernetes `Secret`); see `Dockerfile.dist`.

## The agent's settings, plugins and MCP servers

In a pod nobody signs in through a browser, so the organisation's image supplies what a session loads.

| what | where | how |
|------|-------|-----|
| settings, plugins, skills | baked into the image as a seed folder (`JAGT_AGENT_SEED`) from your plugin marketplace repository | the `Dockerfile.dist` stage clones it with a build secret and records the commit it built from |
| MCP config holding a token | mounted from a `Secret`, its path in `JAGT_MCP_CONFIG` | never in the image |

- HOME lives on the volume, which hides whatever the image bakes under it; so on every start `entrypoint.sh` copies the seed over
  the agent's folder (`JAGT_AGENT_HOME`): settings, MCP config and plugins are replaced, conversations and logins kept.
- With either variable unset, or no seed folder, nothing is copied.
- `claude.mcpConfig` or `assistant.mcpConfig` naming a file the container lacks is one mark on the board's head; it ends
  once the file is there. A value holding the JSON itself is not a file and is not checked.

## Git reaches the code host

- **SSH**: mount the bot's private key (mode 0600) and a `known_hosts` into `/home/jagt/.ssh`; the image has `openssh-client`.
  Without `known_hosts` the first clone fails on host verification: jagt never answers that prompt.
- **Token**: a credential helper in your image reading the token from the environment, with `https://` project urls.
- `codeHost: github` or `gitlab` also needs `gh` or `glab` in your image.

## Your toolchain

Sessions check their work in your image, built `FROM` jagt's: [spec](../openspec/specs/server-environment/spec.md),
[0175](decisions/0175-an-organisation-builds-its-toolchains-onto-jagts-image.md), optional blocks in [`Dockerfile.dist`](../Dockerfile.dist).

## Build and deploy

Your image sits on jagt's: `Dockerfile.dist` adds the agent CLI, `gh` and `glab` as optional blocks.

```sh
docker build -t jagt-base .                                              # in a jagt checkout
docker build -f Dockerfile.dist --build-arg BASE=jagt-base -t my-jagt .
```

**Compose** builds jagt's own image and mounts one volume, `jagt-data`, at `/data`: state, artifacts, repositories,
worktrees and the CLI login. **`/data` must be persistent** — a compose volume, or a PersistentVolumeClaim under
Kubernetes: without it a restart, a deleted pod or a new image loses every task, worktree and login. jagt does not check.

```sh
docker compose up -d --build
curl -s localhost:8290/actuator/health/readiness     # 200 once startup checks are green and clones are done
```

**Kubernetes**: `cp kubernetes.yml.dist kubernetes.yml`, set the image, host and Secret values, `kubectl apply -f kubernetes.yml`
([0179](decisions/0179-the-kubernetes-pod-is-restricted-and-its-jvm-sized.md)).
One replica, `Recreate`, restricted Pod Security Standard, no fixed UID (the image runs as any UID in group 0, as OpenShift
assigns; [0192](decisions/0192-the-image-runs-as-an-arbitrary-uid-and-the-pod-is-quota-sized.md)),
a service account without a token, one optional PVC, Secrets
(identities; the MCP file mounted at `JAGT_MCP_CONFIG`), Service, Ingress (host = `JAGT_PUBLIC_URL`), a `NetworkPolicy`.
[`KubernetesManifestsTest`](../orchestrator-backend/src/test/java/dev/jagt/orchestrator/KubernetesManifestsTest.java) pins them.

| knob | where | default |
|------|-------|---------|
| JVM options | `JAGT_JAVA_OPTIONS`, jagt's JVM alone | heap `-Xmx512m`, serial GC, exit on out-of-memory, dump to `/data/dumps` |
| memory, CPU | `resources` | 2Gi requested, 4Gi limit, 1 CPU requested, 4 limited, small ephemeral storage (estimates) |
| shutdown | `terminationGracePeriodSeconds` | 60: preStop 5 s, `spring.lifecycle.timeout-per-shutdown-phase` 20 s twice |
| probes | `/actuator/health/liveness`, `/actuator/health/readiness` | startup 5 min, liveness 3 misses |
| egress | `NetworkPolicy` | DNS, then HTTPS and SSH to public addresses; add acceptance environments |

- Probes: liveness is the application's own state alone; readiness waits for the start and the clones (below).
- The first clone runs inside startup, before readiness, so a pod is never ready ahead of it. A clone retried after a
  failure does not hold the pod: the board's head shows it.

## Upgrade, back up, roll back

What `/data` (`ORCHESTRATOR_DATA`) holds that outlives an image:

| path | what | when it changes |
|------|------|-----------------|
| `jagt/state.json` | every task, written atomically | every mutation |
| `jagt/state.json.bak` | the version before the last write | every mutation |
| `jagt/state.json.corrupt` | an unreadable primary, set aside while `.bak` stood in | on that recovery |
| `tasks/`, `projects/` | task worktrees (unpushed commits live here), clones | while sessions work |
| `jagt/master-token`, `logs/` | the Master's token (drawn at each start), logs | each start |

- **Read across minors**: a newer jar reads an older `state.json` as it stands: unknown keys are ignored, a missing number or flag
  reads 0 or false. The file is rewritten in the newer shape on the next mutation, the old one kept as `.bak`.
  `StateServiceCompatibilityTest` reads a fixture of every shipped minor (0.9 to 0.23) and a new minor without its fixture fails the build.
- **Unreadable state**: `.bak` takes over and the log says so; with no usable `.bak` jagt refuses to start, never starts empty.
- **Older jar reading a newer file**: untested, unknown. Roll back from a copy, never by pointing the old jar at the new file.

Before an upgrade, stop jagt, then copy the folder (with sessions running only `state.json` is a consistent copy):

```sh
docker compose stop                    # or: kubectl scale deployment/<name> --replicas=0
docker run --rm -v <volume>:/data -v "$PWD":/backup alpine tar czf /backup/jagt-data.tgz -C /data .   # <volume>: docker volume ls
cp "$ORCHESTRATOR_DATA"/jagt/state.json* ~/backup/    # a jar install: the same files in its data folder
```

Roll back: stop, put the previous image tag (or jar) back, restore the copy over `/data` (extract the archive, or copy the two files
back), start, and check `/actuator/health/readiness` and the board's task list.

- Keep the image tag of the running minor before pulling the next one: the manifest or compose file names it.
- Worktrees made after the copy are not in it; their branches are on the code host once pushed.

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

- `<token file>` is `master-token` beside `jagt/state.json`; point a CLI's MCP config at `http://localhost:8290/mcp` with that header.
- Under `auth.mode: password`, `/api/debug/…` needs the login session; `/mcp` needs the token.
- Records: `backend-log`, `master-log`, `stops`, `stops-reviewed`; under `orchestrator.logging: console` the two logs are what stdout carried.
