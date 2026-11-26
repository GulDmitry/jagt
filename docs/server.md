# jagt on a server

[← README](../README.md)

One container, one replica, the board behind a password login. The image holds jagt, git, curl and tmux and no agent CLI
([0171](decisions/0171-the-image-is-a-base-without-agent-clis.md)).
`tini` is PID 1 and jagt runs under a relaunch loop: a jagt killed by a signal starts again and its sessions' tmux
keeps running; any other exit ends the container; stopping the container stops both.

## From compose up to a running task

Work in a full jagt checkout: the image builds from it with Gradle, a download of the release jar alone is not enough.

1. **Hash a password.** The login name is always `owner`; only the password is yours.
   `htpasswd -nBC 10 "" | cut -d: -f2` prints the bcrypt hash (`htpasswd` is on macOS already; Debian and Ubuntu: `apt install apache2-utils`).
2. **Fill `.env`.** `cp examples/server.env .env`, then put the hash in single quotes after `JAGT_PASSWORD_HASH=` (compose reads a bare `$`).
   `JAGT_PUBLIC_URL` stays `http://localhost:8290`; for another host port set `JAGT_PORT=9000` and the URL to
   `http://localhost:9000`, or the board answers 403.
3. **Name your project in `jagt.server.yml.dist`** before the first build: the image bakes it as `/data/jagt.yml`'s seed; a `jagt.yml` is not read.
   Replace the placeholder `url: https://example.com/team/repo.git`, which cannot clone, with a repository the bot can read, and its `baseBranch`.
   A server project has a `url` and no `path`: jagt clones it to `/data/projects/<name>`.
   The clone needs the bot's credentials: an SSH key mounted by `volumes` in the override file below, or
   [Git reaches the code host](#git-reaches-the-code-host).
   A failed clone does not hold readiness (step 5): the board's head names the cause; jagt retries every minute.
   Later starts ignore the `.dist`: edit `/data/jagt.yml` instead (`docker compose cp jagt:/data/jagt.yml .`, edit, copy back, restart).
4. **Give the image an agent.** The compose image holds no agent CLI, so a task cannot start on it.
   Uncomment the Node block and your agent CLI's block in `examples/agent-image.Dockerfile`, then build `jagt-base` first and layer it:
   ```sh
   docker build -t jagt-base .
   echo 'services: {jagt: {build: {context: ., dockerfile: examples/agent-image.Dockerfile, args: {BASE: jagt-base}}}}' > docker-compose.override.yml
   ```
   Compose merges the override on its own. Put the agent's API key in `.env` under the variable your CLI documents.
5. **Start it.** `docker compose up -d --build`, then `curl -s localhost:8290/actuator/health/readiness` until it answers 200.
   It answers 200 once jagt has started and no clone is running, so a failed clone still gives 200: read the board's head.
6. **Log in.** Open `http://localhost:8290`; user `owner`, your password. A script posting to `/login` must send
   `-H 'Origin: http://localhost:8290'`: a write without an `Origin` gets 403.
   Only readiness is public; its `curl` needs no `Host` header while `JAGT_PUBLIC_URL` names `localhost`. With another host
   or a `JAGT_PORT` other than 8290, add `-H 'Host: <that host>'`, or `NotThePage` answers 403.
7. **Start a task.** Press **New task** (or `t`), pick your project and watch its session start on the card.
   No tracker is needed: `orchestrator.tracker.mode` is `off`, and `JAGT_MCP_CONFIG` and `JAGT_TRACKER_USER` may stay unset
   (set them for tracker intake or tools: [identities](#the-three-identities)).
   The Master runs in `act`, the default: it pushes and opens requests; the deploy is yours, but an approved request
   deploys itself (`roles.master`, `deploy.onApproval` in [`jagt.server.yml.dist`](../jagt.server.yml.dist)).

## Bot accounts and secrets

One account per service, never your own: a code-host account cloning and pushing task branches, and the agent CLI's; a leaked key costs one service.
Their secrets live in `.env` for compose or a `Secret` for Kubernetes (`examples/server.env`), never in `jagt.yml` or an image.
Verify a published image: `gh attestation verify oci://ghcr.io/guldmitry/jagt:<version> --repo GulDmitry/jagt`.
A pinned image stays pullable 90 days at least, the latest patch of the last two minors for good: mirror the image you pin.

## Configuration

The image keeps `jagt.yml` at `/data/jagt.yml` on the volume, so a board edit survives a recreate; a first start copies a mounted `/app/jagt.yml`,
else `jagt.server.yml.dist`, there. `JAGT_CONFIG_FROM_IMAGE=true` copies the image's file over it each start: new keys apply, board edits are lost.

- `jagt.server.yml.dist` opens no terminal window, needs no kitty, notifier, editor or keep-awake, and logs to the console.
  Each key is described in `jagt.desktop.yml.dist` under the same name, and nowhere else.
- One file per environment: keep `jagt.stage.yml` and `jagt.prod.yml` and set `ORCHESTRATOR_CONFIG_FILE=/etc/jagt/jagt.prod.yml`;
  jagt reads that file alone, no profile or overlay involved.
- `agent.sandbox.enabled` stays `false` in a pod: the container is the fence. A session still reaches the Master token
  and the code-host credential, because the pod runs one OS user ([threat model](threat-model.md#gaps)).
- `usageLimits.enabled` stays `false` with an API key: the limits are read from an agent CLI's own login, which a key lacks.
- Verify runs go one task at a time: size the pod's limits for one `verifyCommand` beside the sessions.
- A `verifyCommand`, `setupCommand` or acceptance flows program the image lacks shows in the board's head: extend the image
  (`examples/agent-image.Dockerfile` has the Node and Chromium layers).
- The host of `JAGT_PUBLIC_URL` is the one the ingress must forward; any other `Host` or `Origin` is refused with 403.
- The ingress ends TLS (the manifest's `tls` Secret); the starter reads `X-Forwarded-*`, sets the cookie Secure and ends an idle login after 30 min.
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
- The provider is discovered on the first login, so one that is down does not stop jagt starting; `email` is read from the id token alone,
  and a provider that leaves it out matches on the subject.

## The three identities

Each is set by your image's environment or a mounted file, never a value in `jagt.yml` or the image.

| identity | what it is | set by |
|----------|------------|--------|
| tracker | the MCP servers a session and a read reach the tracker through, and the user `take` starts for | a mounted file whose path `JAGT_MCP_CONFIG` holds, `JAGT_TRACKER_USER` |
| code host | the account `gh` or `glab` acts as | `GH_TOKEN` or `GITLAB_TOKEN` (`GITLAB_HOST` for a self-hosted host) in the environment |
| notification | the channel's webhook | the starter's commented `chat` channel: `JAGT_NOTIFY_WEBHOOK` holds the URL, `webhookEnv` names it; unset, jagt starts without the channel and its board's head says so |

- The mounted MCP file carries the tracker's own `${VAR}` secrets; a `gh` or `glab` login under HOME is only their fallback.
- Pass the variables at run time (`docker run -e`, `.env`, a `Secret`): `examples/agent-image.Dockerfile`.

## The agent's settings, plugins and MCP servers

In a pod nobody signs in through a browser, so the organisation's image supplies what a session loads.

| what | where | how |
|------|-------|-----|
| settings, plugins, skills | baked into the image as a seed folder (`JAGT_AGENT_SEED`) from your plugin marketplace repository | the `examples/agent-image.Dockerfile` stage clones it with a build secret and records the commit it built from |
| MCP config holding a token | mounted from a `Secret`, its path in `JAGT_MCP_CONFIG` | never in the image |

- HOME is `/data/home`, on the volume: a revived session finds its conversation and login after a new pod. The volume hides whatever the
  image bakes under it; so on every start `entrypoint.sh` copies the seed over the agent's folder (`JAGT_AGENT_HOME`):
  settings, MCP config and plugins are replaced, conversations and logins kept; with either variable unset nothing is copied.
- jagt marks the agent's first-run onboarding done in `<home>/.claude.json` when it provisions a worktree, keeping every other key;
  the seed folder needs no `.claude.json`. A window still showing the first-run picker is reported as waiting on it.
- `claude.mcpConfig` or `assistant.mcpConfig` naming a file the container lacks is one mark on the board's head, until the file is there.
  A value holding the JSON itself is not a file and is not checked.
- A plugin server needing a browser login cannot sign in here: list its plugin (`name@marketplace`) under `claude.disabledPlugins`
  and no session or read loads it. Any other server not connected is one head mark, probed every ten minutes.

## Git reaches the code host

- **SSH**: mount the bot's private key (mode 0600) and a `known_hosts` into `/data/home/.ssh`; the image has `openssh-client`.
  Without `known_hosts` the first clone fails on host verification; jagt never answers that prompt.
- **Token**: a credential helper in your image reading the token from the environment, with `https://` project urls.
- `codeHost: github` or `gitlab` also needs `gh` or `glab` in your image.

## Build and deploy

Sessions check their work in your image, built `FROM` jagt's: [spec](../openspec/specs/server-environment/spec.md),
[0175](decisions/0175-an-organisation-builds-its-toolchains-onto-jagts-image.md).
`examples/agent-image.Dockerfile` adds the agent CLI, `gh` and `glab` as optional blocks; for Kubernetes build it with
`docker build -f examples/agent-image.Dockerfile --build-arg BASE=jagt-base -t my-jagt .` after `docker build -t jagt-base .`.
A cluster that forbids image builds inside it needs the image built elsewhere. Build it there and push it to a registry the cluster pulls from:
`docker buildx build --platform linux/amd64 --ssh default -f examples/agent-image.Dockerfile --build-arg BASE=<registry>/jagt-base \
-t <registry>/my-jagt --push .`.
The `--ssh default` flag lets the build clone a private marketplace through your SSH agent, so no token enters the build.

**Compose** mounts one volume, `jagt-data`, at `/data`: state, artifacts, repositories, worktrees and the CLI login.
**`/data` must be persistent** — a compose volume, or a PersistentVolumeClaim under
Kubernetes: without it a restart, a deleted pod or a new image loses every task, worktree and login. jagt does not check.

**Kubernetes**: `cp examples/kubernetes.yml kubernetes.yml`, set the image, host and Secret values, `kubectl apply -f kubernetes.yml`
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
| temporary files, caches | `TMPDIR`, `XDG_CACHE_HOME` | unset; with a tight ephemeral-storage quota set them under `/data`, and each start creates them |
| shutdown | `terminationGracePeriodSeconds` | 60: preStop 5 s, `spring.lifecycle.timeout-per-shutdown-phase` 20 s twice |
| probes | `/actuator/health/liveness`, `/actuator/health/readiness` | startup 5 min; liveness is the application's own state alone, 6 misses of 10 s, as a restart ends every session and only a hung JVM warrants one; readiness holds only while a clone runs (see step 5 above) |
| ingress controller | `NetworkPolicy` `from` | namespace `ingress-nginx`; on OpenShift swap in the commented selector `policy-group.network.openshift.io/ingress` |
| route timeout | Ingress annotation | OpenShift turns the Ingress into a Route: enable `haproxy.router.openshift.io/timeout` (the board streams); ingress-nginx has its own two |
| egress | `NetworkPolicy` | DNS, then HTTPS and SSH to public addresses; add acceptance environments |

## Upgrade, back up, roll back

What `/data` holds, what a newer jar reads and the stop-copy-restore steps: [server-upgrade](server-upgrade.md).

## Debug from a local CLI

A session reads a running server, never changes it. Its way in is jagt's own debug reads on `/mcp`, through a port-forward
([debug-reads](../openspec/specs/debug-reads/spec.md#requirement-a-running-jagt-is-read-never-changed-through-its-own-door)).
They answer from the records and the state, so the session needs no shell in the pod for most questions.
The board is the person's view and needs a browser login: it has no view of the debug records.

1. **Forward the port.** `kubectl port-forward <pod> 8290:8290`. Under compose the published port is already `localhost:8290`.
2. **Read the token into a variable.** `TOKEN=$(kubectl exec <pod> -- cat /data/jagt/master-token)`; compose:
   `docker compose exec -T jagt cat /data/jagt/master-token`. Never print it or write it into a file; it makes its holder the Master and
   changes at each start.
3. **Call a tool.** Point the CLI's MCP config at `http://localhost:8290/mcp` with the header `X-Jagt-Master: <the token>`, or use `curl`:
   ```sh
   curl -s -H "X-Jagt-Master: $TOKEN" -H 'Content-Type: application/json' localhost:8290/mcp \
     -d '{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"read_record","arguments":{"record":"stops"}}}'
   ```

| question | command |
|----------|---------|
| what is stuck, what is every task doing | `list_tasks`; with a login, `GET /state` |
| why a task stopped | `read_record` `stops`, then `stops-reviewed`; the cause of a failure is in `backend-log` |
| what jagt itself did or failed at | `read_record` `backend-log`, `master-log` (under `logging: console` the two are what stdout carried) |
| what a session said and did | `read_transcript` with the task id |
| what a session shows now | `read_screen` with the task id, or `GET /api/debug/screen?taskId=`: no key is sent |
| is the JVM alive, how loaded | `curl -s localhost:8290/actuator/health/liveness`; `/actuator/metrics/jvm.threads.live`, `jvm.memory.used` (login, else loopback) |
| a JVM hang | `read_record` `threads`, or `GET /api/debug/records/threads`: every thread's name, state, lock and stack |
| a JVM out of memory | the heap dump in `/data/dumps` (`kubectl cp`); the JVM exits and the loop starts it again |

- `read_record` and `read_screen` return the last `lines` (at most 1000); `read_transcript` the newest `maxChars`. Ask for a tail, then widen.
- The image has a JRE, so no `jcmd`, `jfr` or `jstack`. `read_record` `threads` needs none of them.
- Never open a JDWP port: a debugger port is remote code execution in the pod, which holds the Master token.
- `kubectl exec` runs as the pod's one OS user; do not read `env` or `.env`: they hold the bot's keys.
