# Templates and structured text in model prompts — research, 2026-10-06

Every external claim carries `[sN]`, resolved in [Sources](#6-sources). **(exp)** marks this document's own
experiment (§3.2); **(local)** marks a measurement on this machine with Claude Code 2.1.285.

## 1. TL;DR

| question | verdict | confidence |
|----------|---------|------------|
| Q1 templates: cost | no difference: the model sees the same string | high |
| Q1 templates: cache | indirect only: a template makes "constant first, values last" checkable [s7] | high |
| Q1 templates: determinism | none; reruns vary with serving, not with how the string was built [s11] | high |
| Q2a tokens | key=value saves ≤ 14 tokens per short message **(exp)**; JSON ≈ prose; not a lever | high |
| Q2b accuracy | no difference on short jagt-like messages (Haiku) **(exp)**; structure wins on many records [s5] | medium |
| Q2c stability | no difference: 100% rerun and paraphrase agreement in both forms **(exp)** | medium, small n |
| Q2d cache | form is irrelevant, position is everything [s7]; real lever: per-worktree prompt sections **(local)** | high |

## 2. Q1 — templates vs concatenation

### Evidence

| fact | source |
|------|--------|
| a cache hit needs a 100% identical prefix up to the breakpoint | [s7] |
| prefix order is `tools` → `system` → `messages`; a change invalidates its level and all below | [s7] |
| each breakpoint looks back at most 20 blocks | [s7] |
| OpenAI: keep static content first, dynamic later, since hits need exact prefix matches | [s10] |
| identical temperature-0 requests gave 80 distinct completions in 1000 runs (batch-dependent kernels) | [s11] |

A template and a concatenation rendering the same characters are the same request: same tokens, same cache key, same
output distribution. What a template buys:

| benefit | why |
|---------|-----|
| cache order is enforceable | slots are visible, so a test can assert "no slot before the constant part ends" |
| one prefix per call kind | values interleaved in prose move the first differing token forward |
| no swapped arguments | `sub-agent-context.md` fills 16 positional `%s`; a named slot cannot be misplaced |
| the text is reviewable | one file, diffable, counted by `TextBudgetTest` |

### "Send the template once, then only its name and parameters"

Not possible with today's Claude APIs or CLIs, and it was not cheaper where it existed.

| channel | what happens |
|---------|--------------|
| Messages API | stateless; every request carries the full text; cache reads cost 0.1× base, still in context [s7] |
| cache lifetime | 5 min default, 1 h at 2× write price; an idle template is written again [s7] |
| Claude Code `-p` | each one-shot sends its own system prompt and tools; reuse is the same prefix cache |
| OpenAI prompt objects | stored a template by `id` + `variables`; shut down 2026-11-30; OpenAI now says: templates in code [s10] |
| long-lived session | the closest thing: the brief is sent once, later turns add the delta, read from cache |

### Verdict

Templates are worth it for **ordering and correctness**, not tokens. Concatenation that also puts values last earns
the same cache hits; a template makes that order hold and testable.

## 3. Q2 — prose vs structured form

### 3.1 Literature

| study | models | finding |
|-------|--------|---------|
| Sclar et al., ICLR 2024 [s1] | open, LLaMA-2-13B | spurious format changes move accuracy up to 76 points; size and tuning do not remove it |
| He et al., 2024 [s2] | GPT-3.5, GPT-4 | text/Markdown/JSON/YAML moves results up to 40%; GPT-4 far more robust |
| Hua et al., EMNLP 2025 [s3] | 7 frontier | most measured sensitivity is rigid answer matching; with an LLM judge variance drops |
| Tam et al., 2024 [s4] | several | forcing **output** into JSON/XML/YAML degrades reasoning |
| Improving Agents, 2025 [s5] | GPT-4.1-nano | 1000 records: Markdown-KV 60.7%, YAML 54.7%, JSON 52.3%, prose 49.6%, CSV 44.3% |
| Notation Matters, 2026 [s6] | open 17–32B | compact notations save 2–27% input, cost up to 9–14 accuracy points |
| Anthropic [s8] | current Claude | XML tags separate instructions, context and input; data first, query last (up to 30%) |
| Anthropic [s9] | current Claude | constrained decoding guarantees schema-valid output |

- **Frontier models**: format robustness is much higher than on small or older ones [s2][s3]; separating instruction
  from data is the one effect the vendor documents [s8].
- **Shown only on small or older models**: the 40–76-point swings [s1][s2] and the compact-notation trade-offs [s6].
- **Many records**: explicit labels beat tables and prose when one value must be found among many [s5]; Markdown-KV
  used 2.7× CSV's tokens to get there.

### 3.2 Experiment

`claude -p --model haiku` (resolved to `claude-haiku-4-5-20251001`), `--output-format json`, `--tools ""`, a one-line
`--system-prompt`. Harness `harness.py` and `analyze.py` in the session scratchpad, outside the repo. Three jagt-like
tasks, 10 inputs each, both forms carrying the same facts; prose with distractors ("no longer red", a sibling task,
"question already answered"). Runs 1–2 share one instruction; run 3 uses a paraphrase. 190 calls, $0.34.

| task | prose example | key=value example |
|------|---------------|-------------------|
| i extract 4 fields | `Task ABC-42 on feat/abc-42-login is in its third round, and its checks are red again.` | `task=ABC-42 … checks=red` |
| ii ask/wait/fix/ship | `Checks are green, no comment is open, but the agent asks whether to keep v2.` | `checks=green … open_question=…` |
| iii question? yes/no | `The agent working on task ABC-42, in status IN_PROGRESS, reported: …` | `status=… message="…"` |

| task | form | calls | input tok | output tok (thinking) | correct | rerun agree | paraphrase agree |
|------|------|-------|-----------|-----------------------|---------|-------------|------------------|
| i | prose | 30 | 741 | 272 (218) | 30/30 | 10/10 | 10/10 |
| i | key=value | 30 | 734 | 201 (146) | 30/30 | 10/10 | 10/10 |
| i | JSON | 10 | 740 | 242 (188) | 10/10 | – | – |
| ii | prose | 30 | 751 | 214 (208) | 30/30 | 10/10 | 10/10 |
| ii | key=value | 30 | 752 | 209 (203) | 30/30 | 10/10 | 10/10 |
| iii | prose | 30 | 730 | 174 (168) | 27/30 | 10/10 | 10/10 |
| iii | key=value | 30 | 726 | 197 (190) | 27/30 | 10/10 | 10/10 |

- A one-character prompt costs 665 input tokens, so the message part is ~60–90. key=value saves 2–14 tokens on task i
  and is up to 8 tokens **longer** on task ii (`unresolved_comments=` splits into several tokens).
- The only error, 3/3 in **both** forms: `Pushed round 2. Let me know if anything else is needed.` read as a question.
- Thinking tokens moved: prose +71 per call on extraction (8 of 10 inputs), −22 on classification. Output costs 5×
  input, so this outweighs the input saving, and its sign flips by task.
- No call read the cache: these prompts sit below Haiku 4.5's 4096-token minimum [s7].

### 3.3 Cache across worktrees (local)

Two one-shots with the default system prompt in two directories, as jagt's headless reads run in two worktrees:

| flags | 1st written | 2nd read | 2nd written |
|-------|-------------|----------|-------------|
| default | 21,550 | 14,437 | 7,113 |
| `--exclude-dynamic-system-prompt-sections` | 21,532 | 18,299 | 3,233 |

The flag moves cwd, env info, memory paths and git status from the system prompt into the first user message, and
only with the default system prompt (`claude --help`). The CLI wrote with the 1-hour TTL, billed 2× base [s7]. n = 1.

### 3.4 Verdict per sub-question

| | verdict |
|-|---------|
| (a) tokens | key=value a few tokens shorter, JSON ≈ prose, YAML/XML longer [s5]; invisible next to a 15–20k prefix |
| (b) accuracy | no difference on short messages; pays off when a value hides among many [s5] or data mixes with rules [s8] |
| (c) stability | equal; rerun noise comes from serving [s11]; a parser or a schema [s9] is what makes an answer stable |
| (d) cache | form irrelevant, position decisive [s7]; per-worktree sections cost ~3.9k extra written tokens **(local)** |

## 4. What it means for jagt

The 52% cache-write share does not come from prose or concatenation: it is the prefix diverging per worktree and per
call kind. Structure belongs where **code** reads the string, not where a model does. The headless reads are
`HeadlessRoundReviewer` and the assistant; the parsed strings are `outcome=question — …` and the status messages;
the briefs are `sub-agent-context.md` and `task_context.md`.

| where | change | why |
|-------|--------|-----|
| headless one-shots | `--exclude-dynamic-system-prompt-sections`; only needed tools | cwd and git status leave the system block [s7] |
| strings code parses back | event word + `key=value`, or `--json-schema` as `RoundRead` does | a parser is deterministic, a regex on prose is not |
| briefs, relays | rules in prose; values in one labelled block at the end; named slots | shared across tasks only if constant text leads |
| Master prompts | `<instructions>`, `<round>`, `<question>` tags; question last [s8] | the format effect documented for current models |
| model output | schema only where code consumes it; free text where it reasons [s4][s9] | a forced format can cost reasoning [s4] |

`sub-agent-context.md` opens with `task %s`, so none of its ~2,500 words is shared across tasks. One write per task
start against hundreds of runs makes that a small gain; the headless reads carry the volume.

Rule proposal:

> A prompt is a template file: constant text first, every value in one labelled block at its end, slots named.
> A string code parses back is `event key=value …` or schema-validated JSON — never prose read by a regex.
> Instructions and data are separated by XML tags; the question comes last.
> A headless read keeps per-machine sections out of the system prompt and carries only the tools it needs.
> A value's form is not tuned for tokens; its position is tuned for the cache.

## 5. Limits and open questions

| limit or question | what would settle it |
|-------------------|----------------------|
| one light model, 190 calls, near-ceiling accuracy: shows "no harm", not "a gain" | harder inputs (5+ tasks per message), the Master's model |
| §3.2 prompts were below the cache minimum | a 20-call run per jagt read kind with the real command line |
| §3.3 is n = 1 per row | repeat in two real worktrees with the `HeadlessRoundReviewer` flags |
| how much of the 52% writes is TTL expiry vs divergence | log cache write/read per call kind with the gap since the last call |
| does `--append-system-prompt round.shared()` vary per task | if it does, move its values into the user message |
| thinking-token swing by form (+71 / −22) | larger n per task; the only effect seen, and its sign flips |

## 6. Sources

- [s1] Sclar et al., "Quantifying Language Models' Sensitivity to Spurious Features in Prompt Design", ICLR 2024 —
  https://arxiv.org/abs/2310.11324
- [s2] He et al., "Does Prompt Formatting Have Any Impact on LLM Performance?", 2024 — https://arxiv.org/abs/2411.10541
- [s3] Hua et al., "Flaw or Artifact? Rethinking Prompt Sensitivity in Evaluating LLMs", EMNLP 2025 —
  https://arxiv.org/abs/2509.01790
- [s4] Tam et al., "Let Me Speak Freely? A Study on the Impact of Format Restrictions on Performance of LLMs", 2024 —
  https://arxiv.org/abs/2408.02442
- [s5] Improving Agents, "Which Table Format Do LLMs Understand Best?", 2025-09-30 —
  https://www.improvingagents.com/blog/best-input-data-format-for-llms
- [s6] "Notation Matters: A Benchmark Study of Token-Optimized Formats in Agentic AI Systems", 2026 —
  https://arxiv.org/html/2605.29676v2
- [s7] Anthropic, Prompt caching — https://platform.claude.com/docs/en/build-with-claude/prompt-caching
- [s8] Anthropic, Prompting best practices —
  https://platform.claude.com/docs/en/build-with-claude/prompt-engineering/claude-prompting-best-practices
- [s9] Anthropic, Structured outputs — https://platform.claude.com/docs/en/build-with-claude/structured-outputs
- [s10] OpenAI, Migrate from prompt objects —
  https://developers.openai.com/api/docs/guides/prompting/migrate-from-prompt-object
- [s11] Thinking Machines, "Defeating Nondeterminism in LLM Inference" —
  https://thinkingmachines.ai/blog/defeating-nondeterminism-in-llm-inference/
