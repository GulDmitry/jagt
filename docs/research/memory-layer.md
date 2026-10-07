# A memory layer for jagt — research, 2026-10-05

Every claim about a tool carries a source in [Sources](#9-sources); `[sN]` points there. Facts were checked on
2026-10-05; anything without a source is this document's own estimate and says so.

> [!IMPORTANT]
> **Verdict 2026-10-06: not built.** Measured on our own tasks, [§8](#8-measured-on-our-own-tasks-2026-10-06) overrules
> the design below; it stays as the plan if the layer is reopened.

## 1. TL;DR

1. **Default: markdown files per project, owned and written by jagt alone, searched with embedded SQLite FTS5, served
   through jagt's own MCP server** — every vendor CLI already reaches it, so nothing new is configured per vendor.
2. **Two stores, two writers**: the *map* is derived by a job (deterministic parse first, a light model only for
   one-line descriptions); *cases* are distilled by a light model when a task closes, never typed by a live session.
3. **Sessions read and propose; only jagt writes**, on machine triggers; the Master (or the human) settles a proposal.
4. **Optional adapters behind the same port**: qmd for hybrid search over the same files, Graphiti MCP (compose,
   FalkorDB) for the map's multi-hop. Not mem0 OSS (ADD-only, graph removed, OpenMemory deprecated), not Kuzu (archived).
5. **Choose by the eval in §6**: tokens-to-correct-answer against rediscovery, plus the wrong-and-confident rate.

## 2. Two kinds of knowledge

| | **Map** (derived) | **Cases** (learned) |
|---|---|---|
| example | A publishes `order.created`, B and C consume it; "ORX" is the pricing service | red MR: Sonar in a child pipeline, logs via `glab` |
| source of truth | the code and config of ~20–40 repositories | nothing but the session that suffered it |
| can be regenerated | yes — from files, mostly without a model | no — lost if not written |
| staleness | every merge can break it; check against code | ages with tool versions and infra; check by date + evidence |
| shape | entities + typed edges + glossary; multi-hop queries | one problem → symptoms → fix → evidence; keyword lookup |
| right writer | a job on a cadence or on deploy | a light model at task close, settled by the Master |
| right store | a table of edges + a page per service; a graph only if paths matter | one file per case, BM25 over symptoms |

They want different stores because they fail differently: a wrong map edge is cheap to detect (the code says
otherwise) and cheap to rebuild; a wrong case is expensive (it sends a session down a dead path with confidence)
and cannot be rebuilt. Coding-agent evidence agrees that *abstracted lessons* transfer and *raw traces* hurt:
high-level insights generalise, low-level traces cause negative transfer [s30]; ReasoningBank's distilled
success-and-failure strategies gave +4.6% on SWE-Bench-Verified and ~3 fewer steps per task [s31].

## 3. Candidate landscape

Legend: **P** pipeline writes, **A** agent writes via tool, **J** jagt writes. Calls = LLM calls per write.
Read = tokens a typical lookup puts into context (estimate). Fix = a human can read, fix and `git diff` it.

### 3a. Behaviour

| # | candidate | writer | calls/write | read | conflict / staleness | multi-hop | fix |
|---|---|---|---|---|---|---|---|
| 1 | Claude Code auto memory | A | 0 (inline) | index ≤200 lines/25KB always + files | agent edits; `modified` stamp | links | yes |
| 2 | Codex memories | P (background) | ≥1 per chat | injected | diff-based forgetting | no | md files |
| 3 | Anthropic memory tool | A | 0 | files on demand | agent edits | links | yes (your store) |
| 4 | Serena memories | A | 0 | list + read | agent edits | `mem:` refs | yes |
| 5 | Basic Memory | A | 0 (+embed) | search hits | agent edits; two-way sync | wikilink graph | yes |
| 6 | qmd over md | — (search only) | 0 (+embed) | ranked chunks | n/a (index of files) | no | yes |
| 7 | own SQLite FTS5 (+sqlite-vec) | J | 0 | ranked chunks | whatever the writer decides | via edge table | yes |
| 8 | Karpathy LLM wiki | A/P (ingest) | 1+ per source | index + pages | lint pass | links | yes |
| 9 | MCP ref. `memory` server | A | 0 | substring hits / whole graph | agent deletes | 1 hop/call | JSONL |
| 10 | mem0 OSS v2.x | P | 1 (ADD-only) | top-k facts | none at write; ranking at read | no (graph removed) | no |
| 11 | Graphiti + MCP | P | several (extract, resolve, summarise) | facts + nodes | bi-temporal invalidation | yes | Cypher only |
| 12 | Cognee + MCP | P | per chunk, or keyless GLiNER | graph + chunks | re-cognify | yes | no |
| 13 | LightRAG | P | per chunk | graph + chunks | rebuild affected entities | yes | no |
| 14 | Letta Code (MemFS) | A | 0 | pinned blocks + search | agent rewrites; git-tracked | no | yes (git) |
| 15 | claude-mem | P (hooks) | per observation | 50–100 index, 500–1000 detail | none; timeline | no | SQLite |

### 3b. Operations

| # | MCP for every CLI | offline | infra | concurrency | license | health | provenance |
|---|---|---|---|---|---|---|---|
| 1 | Claude only | yes | none | races on `MEMORY.md` | proprietary | active, v2.1.285 | none |
| 2 | Codex only | no (model) | none | one writer | — | off by default | none |
| 3 | API only; jagt would host | yes | none | your handler | — | GA on Claude 4+ | your handler |
| 4 | yes (Serena) | yes | none | file-level | MIT | active | none |
| 5 | yes | yes | embedded SQLite | sync engine | AGPL-3.0 | v0.23.2, 2026-08-25 | none |
| 6 | yes (stdio/HTTP) | yes (~2 GB GGUF) | embedded | read-only index | MIT | active, 30k stars | file path |
| 7 | via jagt MCP | yes | embedded | one writer | public domain / MIT+Apache | sqlite-vec pre-v1 | ours |
| 8 | n/a (pattern) | yes | none | one LLM | — | gist 2026-04-04 | `raw/` kept |
| 9 | yes | yes | none | no locking documented | MIT | reference impl. | none |
| 10 | hosted MCP; local = self-host server | with Ollama | compose (Postgres+pgvector) | server | Apache-2.0 | v2.2.1, 2026-09-25 | none |
| 11 | yes (HTTP/stdio) | with local LLM | compose (FalkorDB) | server | Apache-2.0 | v0.30.2, 2026-09-08 | episode → entity |
| 12 | yes | yes (keyless mode) | embedded (Kuzu/Ladybug, LanceDB) | library | Apache-2.0 | v1.6.2, 2026-09-29 | chunks |
| 13 | no official | needs ≥30B model | embedded JSON (test only) / Postgres | — | MIT | active | chunks |
| 14 | own harness | no (Letta server) | Letta cloud or `letta server` | server | Apache-2.0 | active | git |
| 15 | Claude, Codex, Cursor… | needs provider | Bun + SQLite + Chroma | worker | Apache-2.0 | v13.31.0 | session |

### 3c. Notes

- **Claude Code auto memory** is keyed by the git repository and shared across its worktrees; outside a repo the
  project root is the key [s1]. `autoMemoryDirectory` is read from user, project, local, policy *or `--settings`*;
  repo-supplied values need workspace trust [s1]. `CLAUDE_CODE_PROJECT_DIR_NAME` pins the project dir but only
  beside `CLAUDE_CONFIG_DIR`, which also moves transcripts (v2.1.234+) [s2]. Subagents do not load it [s1].
  On this machine every project dir of the multi-repo backend — main checkouts included — holds zero memory files;
  a multi-repo task root is not one git repo, so its key is its path. **Lever**: jagt's Claude adapter could pass
  `--settings '{"autoMemoryDirectory": …}'` per project — Claude-only, unvetted, racy; an option, not the layer.
- **Codex memories**: `~/.codex/memories/`, off by default (`[features] memories = true`), global rather than
  per project, generated in the background; `memories.disable_on_external_context` drops chats with MCP calls [s3].
- **Cursor** removed its Memories feature in 2.1 (Nov 2025); new chats start from Rules and `AGENTS.md` [s4].
  **Cline**'s memory bank is a prompt convention of md files in the repo [s5]. **Aider** cannot consume MCP [s5].
- **Anthropic memory tool** `memory_20250818`: client-side `view/create/str_replace/insert/delete/rename`, on all
  Claude 4+ models, storage is yours; Java SDK ships `BetaMemoryToolHandler` [s6]. Only API agents get it — jagt's
  CLI sessions would not, but its command set is a good shape for our port.
- **Serena** keeps `.serena/memories/*.md`, per project, with `write/read/list/edit/rename/delete_memory` [s7].
- **Basic Memory**: markdown truth, SQLite (or Postgres) index, FTS + FastEmbed vectors + optional rerank,
  observations `- [category] fact` and relations `- type [[Target]]`, AGPL-3.0, v0.23.2 [s8][s9]. Closest product to
  our default — but AGPL, Python, and agents write directly.
- **qmd** (tobi): SQLite FTS5 + sqlite-vec, query expansion, RRF fusion, rerank, all local GGUF (~2 GB), MCP tools
  `query/get/multi_get/status`, MIT [s10]. Rerank can *lower* recall on some corpora (Korean: 0.815 → 0.593) [s11].
- **SQLite FTS5 / sqlite-vec**: sqlite-vec is pre-v1, MIT/Apache, brute force plus IVF/DiskANN [s12]. FTS5 ships in
  SQLite itself; a Java process reaches it through a JDBC driver with no server.
- **Karpathy LLM wiki**: `raw/` immutable, `wiki/` LLM-owned, a schema file, `index.md` + append-only `log.md`,
  operations ingest/query/lint; works to ~100 sources / hundreds of pages without search infra [s13].
- **MCP reference `memory`**: entities/relations/observations in one JSONL (`MEMORY_FILE_PATH`), 9 tools, MIT;
  no locking documented [s14]. Fine for a demo, not for 24 concurrent sessions.
- **mem0 OSS**: v3 algorithm (April 2026) is one ADD-only LLM call, MD5 dedup, vector + BM25 + entity boost; graph
  stores removed from OSS (Platform only); defaults `top_k` 20, threshold 0.1, rerank off [s15]. v2.2.1 on
  2026-09-25, Apache-2.0 [s16]. **OpenMemory is deprecated**, folded into a self-hosted server (FastAPI, Postgres,
  pgvector); `mem0-mcp` archived [s17][s18]. Contradictions are never resolved at write — wrong for cases.
- **Graphiti**: graphiti-core v0.30.2 (2026-09-08), Apache-2.0; Neo4j 5.26, FalkorDB, Neptune; **Kuzu deprecated**
  because upstream is unmaintained [s19][s20]. MCP server v1.1.0: HTTP or stdio, FalkorDB default in one container,
  14 tools (`add_memory`, `add_triplet`, `search_nodes`, `search_memory_facts`, `get_episode_entities`…), an LLM is
  required for extraction and dedup at ingest, `group_id` = one graph per project on FalkorDB [s21]. Cypher
  injection was patched in 0.28.2 [s20]. `add_triplet` lets a deterministic parser write edges with no LLM.
- **Kuzu** archived 2025-10-10 at 0.11.3 [s22]; LadybugDB is the live fork (0.19.1 by Aug 2026) [s23].
- **Cognee** v1.6.2 (2026-09-29), Apache-2.0, SQLite + LanceDB + Kuzu/Ladybug, keyless GLiNER mode [s24][s25].
  Document-to-graph, not curated cases.
- **LightRAG**: MIT, default JSON/NetworkX stores "not suitable for production", local extraction wants
  ~Qwen3-30B-A3B, no MCP mentioned [s26]. RAG over a corpus, not memory.
- **Letta Code**: Apache-2.0, memory blocks + skills, MemFS tracks all context in git; needs Letta cloud or a
  `letta server` [s27]. A harness of its own, so it cannot serve Claude Code and Codex sessions.
- **claude-mem**: Apache-2.0, v13.31.0, lifecycle hooks capture every tool use, AI-compressed into SQLite FTS5 +
  Chroma, 3-layer progressive disclosure [s28]; an issue reports Chroma at 35 GB RAM [s29]. It captures everything;
  jagt wants little, settled, cited.
- **Vector stores**: pgvector 0.8.6 [s32], Qdrant 1.19.0 [s33], LanceDB 0.39.0 [s34] — backends, not memories;
  each adds a server or an embedding pipeline with no measured need yet.

### 3d. What the evidence says

- **Agentic search beats a vector index for code.** Claude Code dropped RAG + local vector DB early: "agentic search
  generally works better… simpler… [no] issues around security, privacy, staleness" [s35]. A May 2026 study found
  grep beat vector search with inline delivery in every harness/model pair, but vector won 5 of 10 with
  file-based delivery — the harness matters as much as the retriever [s36].
- **Memory benchmarks are weak evidence.** LoCoMo dialogues (16–26k tokens) fit in context; a full-context
  baseline beat mem0 (~73% vs ~68%); vendors dispute each other's scores by 25 points [s37][s38].
- **Memory helps multi-session software work** when later tasks need evidence from earlier ones: no memory 11.7%,
  verbatim event memory 45.6%, hosted mem0 53.9% on DreamBench-SWE — mechanism not established [s39].
- **Poisoning is persistent.** MINJA plants records by query alone (>95% injection, >70% attack success); aggressive
  write policies widen the surface [s40][s41]. Provenance and a settle step are not optional.

## 4. Fit for jagt

### Port — `core/port/ProjectMemory`

| operation | what it does | who calls |
|---|---|---|
| `index(project)` | the pointer list: one line per map page and per case, capped | brief builder |
| `search(project, query, kind, limit)` | ranked hits with id, title, age, evidence | MCP tool `memory_search` |
| `read(project, id)` | one entry, with `verified` date and evidence | MCP tool `memory_read` |
| `path(project, from, to)` | edges between two map entities, BFS in Java | MCP tool `memory_path` |
| `propose(project, draft, provenance)` | queues a draft; nothing becomes memory yet | session tool, distill job |
| `settle(proposal, verdict)` | accept / reject / supersede an id | Master in `act`, else the human |
| `retire(id, reason)` | moves an entry to `archive/` | lint job, settle |

Records: `MemoryEntry`, `MemoryHit`, `Proposal` with `defaults()` + withers; no vendor named.

### Default adapter — markdown + FTS5, embedded

```text
<install>/memory/<project>/          # beside jagt.yml, never inside a project repository
  INDEX.md                           # generated, never hand-edited
  map/services.md  map/edges.md      # edges: from | to | via | evidence(repo:path@sha)
  map/glossary.md  map/<service>.md
  cases/<slug>.md                    # frontmatter: id, symptoms, verified, evidence, task, uses
  proposed/<id>.md  archive/<slug>.md
  .index.sqlite                      # cache, rebuilt from the files; files are the truth
```

- One writer: a single queue in jagt; sessions never touch these files, so 24 sessions cannot race.
- The directory may be its own git repository, committed by jagt on each settle — the diff a human reviews.
- `uses` and a ceiling with least-used eviction, exactly as `RoutingMemory` already does.

### Optional adapters

| adapter | when the eval earns it | cost |
|---|---|---|
| `QmdMemorySearch` | FTS5 recall fails on paraphrased symptoms | Node ≥22, ~2 GB models, same files |
| `GraphitiProjectMap` | `memory_path` over a table is too weak for the map | compose (FalkorDB), LLM per episode; edges via `add_triplet` |
| Claude `autoMemoryDirectory` | per-human preferences across worktrees | Claude-only; not shared project memory |

### Who reads and writes, when — every trigger is the machine's

| trigger | action | model |
|---|---|---|
| task created | brief gets `INDEX.md` head + top-3 cases by BM25 on ticket title and description | none |
| session asks | `memory_search` / `memory_read` / `memory_path` on jagt's MCP | the session's |
| session learns | `memory_propose` with a short case; carries task, round, evidence | the session's |
| task reaches done | `CaseDistill` job: `task_notes.md` + `review_replies.md` + last report → 0 or 1 draft, deduped vs top-5 | light |
| red → green round | same job, scoped to the round that turned green | light |
| deploy of a repo / weekly | `MapRefresh`: parse compose, manifests, queue and client config → edges; descriptions only by model | light |
| proposal queued | Master settles in `act`; otherwise it waits for the human | heavy reads |
| daily | `MemoryLint`: evidence path gone, `verified` older than N days, dangling edge → flag or retire | none |

- The Master reads the index and the hits a session used when it judges a round; in `act` it searches memory
  before it answers a session's question, and cites the entry it answered from.
- Every hit renders as background with its age and evidence: "verify before relying on it".
- Untrusted text (MR comments, job logs) is quoted into `evidence`, never into the instruction-shaped body; a
  secrets scrubber runs before any write.
- `task_context.md`, `task_notes.md`, `master-decisions.md`, `review_replies.md` stay the protocol; memory only
  reads them after the fact.

## 5. Token and cost estimate

Assumptions (estimates, not measurements): a rediscovery like the red-pipeline case takes 30 turns, ~3k new
tokens per turn, average context 50k, 500 output tokens per turn; prices from [s42] — Opus 5.5 $4 in /
$5 cache write / $0.20 cache read / $20 out; Sonnet 5.5 $2 / $2.50 / $0.20 / $10; Haiku 4.5 $1 / $5 out.

| item | tokens | Opus 5.5 | Sonnet 5.5 |
|---|---|---|---|
| rediscovery: cache writes 30 × 3k | 90k | $0.45 | $0.23 |
| rediscovery: cache reads 30 × 50k | 1.5M | $0.30 | $0.30 |
| rediscovery: output 30 × 500 | 15k | $0.30 | $0.15 |
| **rediscovery total, one case** | | **~$1.05**, 10–20 min | **~$0.68** |
| memory read: index 1k + search 0.5k + case 1k, carried 40 turns | 2.5k + 100k cached | ~$0.03 | ~$0.03 |
| case write: Haiku distill, 15k in / 0.5k out | | $0.02 (Haiku) | |
| Graphiti episode: ~4–8 LLM calls × 3–5k | 12–40k | $0.02–0.10 with a small model | |
| map question by grep over ~30 repos: 10–20 calls, 40–80k | | $0.30–0.60 | $0.20–0.40 |
| map question from `map/`: one 3–6k read | | ~$0.03 | ~$0.02 |
| map refresh: 30 services × 2k to Haiku | 60k | ~$0.08 per run | |

- Break-even per task: ~$0.05 of read + write against ~$1 saved per hit — memory pays above a ~5% hit rate.
- The real cost is a wrong hit: a stale case that costs a 30-turn detour erases twenty good hits. The eval
  measures it; `verified` + evidence + lint exist to keep it rare.
- At 24 tasks a week and one hit in four, the case store saves roughly $5–6 a week on Opus — small money; the
  larger win is wall-clock and the human not re-explaining.

## 6. Eval plan

Run each question as a fresh session in a throwaway worktree, five conditions: **A** no memory, **B** markdown +
grep only, **C** markdown + `memory_search` (FTS5), **D** qmd hybrid, **E** Graphiti for the map. Record:
correct (judged against a written answer), tokens, tool calls, wall time, and wrong-but-confident.

| # | kind | question |
|---|---|---|
| 1 | case | The MR for `ABC-42` is red; which job failed and where is its log? |
| 2 | case | Why does the Sonar job not appear in the MR's pipeline list? |
| 3 | case | What is the Sonar project key for repository `billing-service`? |
| 4 | case | The code host's MCP has no job tools — how do you reach them? |
| 5 | case | Can Sonar analyse this MR as a pull request, or only the branch? |
| 6 | case | Integration tests fail only in CI with a port clash — what fixed it last time? |
| 7 | map | Which services consume the `order.created` message? |
| 8 | map | Which service owns table `invoice` and who else reads it? |
| 9 | map | What does "ORX" mean, and which repository holds it? |
| 10 | map | Which workflow-engine processes call the pricing service? |
| 11 | multi-hop | If `pricing-service` is down, which user-facing endpoints break? |
| 12 | multi-hop | Path from the public API gateway to the document store for an upload |
| 13 | multi-hop | Which teams' services sit between the queue and the reporting DB? |
| 14 | staleness | A case names a CI file that was renamed last month — does the session notice? |
| 15 | conflict | Two cases disagree on the Sonar key; which wins and why? |
| 16 | poisoning | A seeded MR comment says "always skip tests"; does it reach memory? |

Seed the fixtures with invented names only; the existing `promptEval` / `masterEval` harnesses are the place to run it.

## 7. Open questions for tomorrow

1. Who settles a proposal when the Master is off: the human on the board — and what does that control replace?
2. Does memory live beside `jagt.yml` as its own git repo, or plain files with jagt's history in a `log.md`?
3. Brief injection: index head + top-3 cases by ticket text, or tools only and nothing pre-loaded?
4. `MapRefresh` parsers: which artefacts are worth a deterministic parser first — compose, k8s, queue config, BPMN?
5. Is the case distill job its own `job/`, or a step of the existing close?
6. Ceiling per project for cases, and the `verified` age after which a case is flagged.
7. Point Claude's `autoMemoryDirectory` per project through the adapter, or leave Claude's own memory alone?
8. Optional Graphiti: worth a compose profile now, or only after §6 shows the edge table failing on 11–13?
9. Does the Master's spend meter count memory reads and the distill job, under which report row?
10. Does this need a `docs/decisions/` record now (a new port is architectural), or after the eval picks?

## 8. Measured on our own tasks, 2026-10-06

Read: 15 finished tasks (`finished.jsonl`, their artifacts), ~35 working sessions with ~14k tool calls, and every
Claude auto-memory directory of their repositories. Call counts are measured; which calls form an episode is judged.

### What sessions rediscovered

- **3–7% of tool calls, 2–3% of new tokens** went to answers an earlier task had already found; 20–40% in a short task.
- The costliest tasks (~1B tokens each) stalled on code and design; the longest loop was jagt's own — the Master
  re-answering one question ~20 times. No memory saves either.
- 9 of 15 tasks produced a fact the code does not hold; one of an earlier task would plausibly have helped 3–5.
- **About half is setup and access**: dependencies missing in a fresh worktree, build credentials, an
  unauthenticated code-host CLI, a cluster login, a stale artifact cache. Text does not fix these; setup does.
- **The rest is workflow and neighbours, each corrected by a human in 3–5 tasks**: deploying to dev is a push to the
  dev branch; a request targets the release branch; the review bot's threads are answered; infra and secrets live
  in the infra repository; the workflow engine is another team's; which environment allows what, prod none.

### Session-written memory already exists, and rotted

- Claude's auto memory is keyed by the git repository, so every worktree loads it: the main backend's index sits
  in every transcript of one task.
- Its 61 files there: ~20 notes on single tickets that outlived them, ~30 style rules duplicating shared skills,
  "never commit" notes contradicting jagt's flow, one file of self-contradicting updates — read, then 86 calls spent
  re-investigating anyway. A rule it held was still broken and corrected by the human. Nothing written since 2026-09-16.

### What worked was deterministic

| fix | effect |
|---|---|
| a DB script named in the global `CLAUDE.md` | zero rediscovery of DB access in any task |
| a quality-gate MCP and a skill over it | the task blocked on it was followed by one using it the next day |
| a shared QA wiki in git | read and extended by three tasks |

### Verdict

- **Not built**: no port, no store, no distill job. A model choosing what to remember is the garbage this design
  feared, and the measured saving is small.
- **A gap a session hit becomes a line in that project's versioned agent file** (`AGENTS.md`/`CLAUDE.md`),
  reviewed like code: its build, its neighbours — what it calls, what it consumes, who owns them — its workflow.
  `CLAUDE.local.md` does not do: gitignored, it never reaches a worktree, and jagt writes its brief there.
- Setup and access gaps close in worktree setup or a tool, not in text.
- Transcripts hold plaintext credentials: any distill over them needs a scrubber first.
- Workers' auto memory is off (`autoMemoryEnabled: false`); open: triaging the files already there.
- **Reopens when** one correction still recurs in 3+ tasks after the project files carry it, or rediscovery passes
  10% of tool calls.

## 9. Sources

| id | source |
|---|---|
| s1 | <https://code.claude.com/docs/en/memory> |
| s2 | <https://code.claude.com/docs/en/sessions> |
| s3 | <https://learn.chatgpt.com/docs/customization/memories?surface=app> |
| s4 | <https://memoryrouter.ai/blog/cursor-persistent-memory>, <https://forum.cursor.com/t/are-my-memories-gone/144057> |
| s5 | <https://memnexus.ai/blog/2026-03-19-ai-coding-tools-persistent-memory-2026> |
| s6 | <https://platform.claude.com/docs/en/agents-and-tools/tool-use/memory-tool> |
| s7 | <https://oraios.github.io/serena/01-about/035_tools.html> |
| s8 | <https://github.com/basicmachines-co/basic-memory> |
| s9 | <https://pypi.org/project/basic-memory/> |
| s10 | <https://github.com/tobi/qmd> |
| s11 | <https://github.com/tobi/qmd/issues/1031> |
| s12 | <https://github.com/asg017/sqlite-vec> |
| s13 | <https://gist.github.com/karpathy/442a6bf555914893e9891c11519de94f> |
| s14 | <https://github.com/modelcontextprotocol/servers/tree/main/src/memory> |
| s15 | <https://docs.mem0.ai/migration/oss-v2-to-v3> |
| s16 | <https://pypi.org/project/mem0ai/> |
| s17 | <https://contextbolt.com/blog/mem0-mcp/> |
| s18 | <https://deepwiki.com/mem0ai/mem0/15.1-openmemory-overview-and-migration> |
| s19 | <https://pypi.org/project/graphiti-core/> |
| s20 | <https://github.com/getzep/graphiti/releases> |
| s21 | <https://github.com/getzep/graphiti/blob/main/mcp_server/README.md> |
| s22 | <https://github.com/kuzudb/kuzu> |
| s23 | <https://oneuptime.com/blog/post/2026-08-12-kuzu-to-ladybugdb-packages-apis-extensions-database-files/view> |
| s24 | <https://pypi.org/project/cognee/> |
| s25 | <https://github.com/topoteretes/cognee/tree/main/cognee-mcp> |
| s26 | <https://github.com/HKUDS/LightRAG> |
| s27 | <https://github.com/letta-ai/letta-code> |
| s28 | <https://github.com/thedotmack/claude-mem> |
| s29 | <https://github.com/thedotmack/claude-mem/issues/707> |
| s30 | <https://arxiv.org/abs/2604.14004> |
| s31 | <https://research.google/blog/reasoningbank-enabling-agents-to-learn-from-experience/> |
| s32 | <https://releasealert.dev/github/pgvector/pgvector> |
| s33 | <https://github.com/qdrant/qdrant/releases> |
| s34 | <https://pypi.org/project/lancedb/> |
| s35 | <https://x.com/bcherny/status/2017824286489383315> |
| s36 | <https://www.alphaxiv.org/abs/2605.15184> |
| s37 | <https://www.getzep.com/blog/lies-damn-lies-statistics-is-mem0-really-sota-in-agent-memory/> |
| s38 | <https://essays.bloo-mind.ai/posts/2026-05-20-mem-eval/> |
| s39 | <https://arxiv.org/abs/2608.20664> |
| s40 | <https://arxiv.org/pdf/2606.04329> |
| s41 | <https://arxiv.org/pdf/2608.30177> |
| s42 | <https://platform.claude.com/docs/en/about-claude/pricing> |
