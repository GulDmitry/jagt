# Threat model of jagt's web surface

[← README](../README.md) · Checked 2026-11-16 against the code; a change to a filter, a route or the auth config changes this page
(`ThreatModelTest` pins every filter and every route prefix). Requirement IDs are OWASP ASVS 5.0 chapters, as
[practices](practices.md#security) uses them; section numbers only where that page already names one.

## Assets

| asset | where | why it matters |
|-------|-------|----------------|
| state | `state.json`, `finished.jsonl`, `/data` on a server | tasks, history, who asked; read whole by `GET /state` |
| Master token | `jagt/master-token` beside `state.json`, mode `rw-------`, drawn at each start (`MasterToken`) | whoever presents it is the Master and holds every verb |
| worktrees | one per task, plus the repositories | code a session writes; a push leaves from here |
| session tools | a session's shell, network, Read and Edit tools | run as the session's OS user, which is jagt's |
| code-host credential | an SSH key or `GH_TOKEN`/`GITLAB_TOKEN` in the session's HOME or environment | pushes and opens requests as the bot |
| tracker MCP | the mounted MCP file, its `${VAR}` secrets | reads and writes the tracker as the bot |
| credential references | env names (`passwordHash`, `clientSecretEnv`, `webhookEnv`), a mounted MCP file, `GH_TOKEN`/`GITLAB_TOKEN` | jagt holds names, never values in `jagt.yml` or an image |
| the board | HTTP on `server.address:8290`, default `127.0.0.1` | acts as the human: every verb a human holds |
| the login session | cookie `JSESSIONID`, same-site strict, Secure behind an ingress | what stands for the owner under `password` or `oidc` |

## Actors

| actor | trust | reaches |
|-------|-------|---------|
| owner | full | the board in a browser, a login under `password`/`oidc` |
| Master session | the owner's rights in `act`, bounded by `FlowRules` | `/mcp` with the Master token |
| sub-agent session | its own task only (`CallerScope`) | `/mcp` and `/api/agent/` by its worktree header |
| local process, same user | trusted, a known pass | anything on loopback: forges Origin, reads `master-token` |
| local process, other user or page in a browser | untrusted | loopback by a page's Origin or a rebound Host |
| network peer, server mode | untrusted until login | the ingress's host only |
| tracker, code host | semi-trusted, outbound only | answers jagt's calls; nothing calls in |

## Trust boundaries

| boundary | held by |
|----------|---------|
| browser page, other site, to loopback | `NotThePage` (Host and Origin, Origin on a write) |
| peer off loopback to the board | Spring Security login, `BackedOffPeers` |
| peer off loopback to the session doors | `LoopbackOrRootToken` (Master token) |
| session to another task | `CallerScope`, `McpToolScopeTest` rows |
| session shell to the host | `ToolGate`, `ReadGate`, the pre-push hook ([guards](rules/guards.md)) |
| pod to the network | `NetworkPolicy` ([server](server.md)) |

## Entry points

| entry | auth | filters in front | ASVS |
|-------|------|------------------|------|
| `/api/tasks` (list, create, actions, say, run, key, screen, files, file, diff) | login, else loopback | all | V4, V8 |
| `/api/tasks/code` (tree, file, search) | same | all | V4, V5 |
| `/api/projects` (read, create, update, delete, card), `/api/settings` (read, change) | same | all | V4, V8 |
| `/api/commands`, `/api/jobs`, `/api/interpret`, `/api/interactions`, `/api/windows` | same | all | V4, V8 |
| `/api/events` (server-sent stream) | same | all | V4, V7 |
| `/api/session` | same | all | V7 |
| `/api/debug` (records, transcript; read only) | same | all | V4, V8, V14 |
| `/state` | same | all | V8, V14 |
| `/login.html` and its assets, `/login`, `/logout`, `/login/oauth2` | public, a write carries Origin | all | V6, V7, V10 |
| `/actuator/health` (liveness and readiness) | public | `NotThePage` | V13 |
| `/actuator/metrics`, `/actuator/info` | login, else loopback: not in the contract's public paths | all | V4, V13 |
| `/mcp` | Master token or `X-Working-Directory` of a worktree | `NotThePage`, `LoopbackOrRootToken` | V8, V4 |
| `/api/agent` (tool, read, session, prompt, turn-end, permission) | the same | the same | V8, V4 |
| board assets and `user/` files | public static, no request-driven path | `NotThePage` | V5, V3 |
| hooks under `.jagt/` | a session's own, outbound to `/api/agent` | none: a client | V8 |

Doors, first match wins (`Doors`): `NotThePage`, then `/mcp` and `/api/agent/` (`LoopbackOrRootToken`),
then the board (login, `BackedOffPeers`). Filters: `OriginFilter` (sets who asked, judges nothing),
`AbortedConnectionFilter` (drops an aborted peer's socket error), `RefusedRequests` (answers a refusal without a stack trace).

## Threats

### Spoofing

| threat | defence | holds it | ASVS |
|--------|---------|----------|------|
| a page on another site drives the board | Host and Origin must be loopback or listed | `NotThePage`, `NotThePageTest` | V4, V3 |
| a peer claims to be the owner | BCrypt hash named by env, no default; OIDC subject or email allow-listed | `AuthCheckTest`, `OidcLoginOnTomcatTest` | V6.2, V9, V10 |
| a peer claims to be the Master | constant-time token compare, 256 random bits, new at each start | `MasterToken`, `MasterTokenTest` | V6, V11 |
| a peer names a worktree to act as a session | the doors need the token from a peer off loopback or forwarded | `LoopbackOrRootTokenTest` | V8 |
| password guessing | per-peer backoff keyed on the client Tomcat's `RemoteIpValve` resolves from a trusted proxy, 429 with `Retry-After` | `BackedOffPeersTest`, `PasswordLoginRefusalOnTomcatTest` | V6.3 |

### Tampering

| threat | defence | holds it | ASVS |
|--------|---------|----------|------|
| a cross-site write | a write without Origin is 403; Spring's CSRF is off, Origin and same-site strict stand for it | `NotThePageTest`, 0167 | V4, V3 |
| a path trick skips the Origin exemption | `StrictHttpFirewall` rejects `;params` and encoded dots before a door judges the path | `PasswordLoginRefusalOnTomcatTest` | V4 |
| a session writes outside its task | `CallerScope`, `ToolGate`, `PlanFirst`, the base branch read-only | `McpToolScopeTest`, `ToolGateTest` | V8, V1 |
| a request reads or writes outside a worktree | `../x`, absolute, `.git`, escaping symlink refused | `TaskFilesControllerTest` | V5 |
| `state.json` damaged | atomic write, `.bak`, `.corrupt`; no usable backup throws | `StateServiceTest` | V15 |

### Repudiation

| threat | defence | holds it | ASVS |
|--------|---------|----------|------|
| who asked is lost | every status move keeps its `ActionOrigin`, 50 deep, copied to `finished.jsonl` | `OriginFilterTest` | V16 |
| a login leaves no trace | one structured line per login, never the name or password | `PasswordLoginOutcomeTest` | V16 |
| a refusal leaves no trace | `request refused` with `url` and `cause` | `RefusedRequestsTest` | V16 |

### Information disclosure

| threat | defence | holds it | ASVS |
|--------|---------|----------|------|
| a script or frame from a foreign origin | CSP of the board's own origin, no CDN | `BoardAssetsTest`, `BoardVendorLoadTest`, `BoardCsp` | V3.4 |
| a secret in a log, a card or the config | values only by env name; log filter | `TaskAliasLogFilterTest`, `AuthProperties.toString` | V14 |
| the token file read by another user | mode `rw-------` | `MasterTokenTest` | V14 |
| `/state` or `/api/debug` read by a peer | behind the login; debug reads are `Audience.ROOT` | `GuardedBoard` | V8, V14 |
| management exposes more than health | `include: health` alone | `KubernetesManifestsTest` | V13 |

### Denial of service

| threat | defence | holds it | ASVS |
|--------|---------|----------|------|
| a peer floods a login | per-peer backoff, at most 10000 addresses held | `BackedOffPeersTest` | V6.3 |
| tasks without end | `maxTasks` 50, watchdog, `LoopBreaker` | `task-stops` spec | V2 |
| an idle session stays open | 30 minutes idle on a server | `ServerStarterTest` | V7 |

### Elevation of privilege

| threat | defence | holds it | ASVS |
|--------|---------|----------|------|
| a sub-agent calls a Master verb | the tool list and the call are `Audience.ROOT` only for the root session | `McpToolScopeTest` | V8 |
| a headless read runs a write | `ReadGate` allow-list, fails closed | `ReadGateControllerTest` | V8 |
| a session pushes to a shared branch | `ToolGate`, pre-push hook, host branch protection | `ToolGateTest` | V8, V1 |
| a request path reaches a file | a resource handler only | `BoardFolderTest` | V5 |
| a value reaches a shell | every process starts from an argument list, its binary from configuration; no shell line | `ProcessRunnerTest` | V5 |
| a container escapes | restricted Pod Security, no service-account token, NetworkPolicy | `KubernetesManifestsTest` | V13 |

## Agent sessions

A session is a model run on text it did not write: a ticket, a repository file, a review comment. Its reach is the OS user's.

### What a session reaches

| asset | what it does there |
|-------|--------------------|
| worktrees | reads and writes its own; the tools reach the others by path |
| session tools | runs any shell line `ToolGate` allows, any network call the pod's `NetworkPolicy` allows |
| Master token | reads `master-token` with the Read tool |
| code-host credential | uses it through `git`, `gh`, `glab` |
| tracker MCP | calls the tracker's tools with the bot's rights |

### What a session forges

| forged | how | what stands in |
|--------|-----|----------------|
| `X-Working-Directory` | names another worktree to `/mcp` or `/api/agent` | `LoopbackOrRootTokenTest` off loopback; same-user pass ([0025](decisions/0025-the-board-is-no-way-around-the-token.md)) |
| Master token | reads the file | `ToolGateTest` refuses a shell line naming it; the Read tool passes |
| `state.json` | writes it | `StateServiceTest` restores a damaged file; same-user pass |
| verdict file | writes it with `state.json` | same-user pass ([guards](rules/guards.md)) |
| origin | sends `Origin` from `curl` | `NotThePageTest`; a board write needs Origin or the token |

### OWASP LLM Top 10 2025

| risk | in scope | defence | holds it |
|------|----------|---------|----------|
| LLM01 prompt injection | a ticket, repository file or review comment steers a session | no defence against the text; its effect is bounded to the task | `ToolGateTest`, `McpToolScopeTest`, Gaps |
| LLM02 sensitive information disclosure | a session writes a secret into a log, a card or a request | values only by env name; log filter | `TaskAliasLogFilterTest`, Gaps |
| LLM06 excessive agency | a session pushes, deploys or calls a Master verb | tool list by audience, shell gate, plan first, read-only base branch | `McpToolScopeTest`, `ToolGateTest`, `ReadGateControllerTest` |
| LLM07 system prompt leakage | a session reads its role prompt | prompts hold no secret, credentials come by env name | Gaps |

LLM03, LLM04, LLM05, LLM08, LLM09 and LLM10 name no jagt-owned model, store or endpoint: jagt trains and hosts nothing.

## Gaps

| gap | what stands in for it | open |
|-----|-----------------------|------|
| `/mcp` and `/api/agent/` need no login under `password` | `LoopbackOrRootToken`; the ingress must still block both ([remote access](remote-access.md)) | none |
| a same-user process forges Origin and reads `master-token` | a known pass in [guards](rules/guards.md) | sessions as another user |
| `auth: none` trusts the network | `server.address` stays loopback, a wider one refuses the start | none |
| one owner, one password, no MFA | OIDC delegates MFA | a second login mode |
| no DAST | the filter and refusal tests | a DAST scan |
| no HSTS from jagt | TLS ends at the ingress | none |
| ASVS IDs are chapters, not individual requirements | practices names the same grain | a requirement-level audit |
| a pod session reaches the Master token and the code-host credential | one OS user per pod: the Read tool opens `master-token` and the credential; `NetworkPolicy` and host branch protection bound what a copy does ([0025](decisions/0025-the-board-is-no-way-around-the-token.md)) | sessions as another user |
| a session reads its role prompt, the seed and every file of its worktree | no prompt or seed holds a secret | none |
