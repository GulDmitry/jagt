# Local models

[← README](../README.md)

Opt-in: the cloud stays the default. A local model serves the reads that need no tool; role reads and sessions
stay on the cloud until a model passes their evals here.

## Quick start

| memory | model | file |
|---|---|---|
| 16 GiB | `unsloth/Qwen3.5-4B-GGUF:Q6_K` | 3.5 GB |
| 24 GiB and more | `unsloth/Qwen3.5-9B-GGUF:Q4_K_M` | 5.7 GB |

Leave free disk for the file. A local model serves `COMMAND_MAP` reads only: the 9B maps 12 of 15 commands (the cloud
15 of 15) and judged 0 of 2 acceptance cases in 10 minutes ([measured below](#what-it-serves)).

1. `brew install llama.cpp` (Linux: a release build of [llama.cpp](https://github.com/ggml-org/llama.cpp)).
2. Start the server with the model for your memory; the first run downloads it:

   ```sh
   llama-server -hf unsloth/Qwen3.5-9B-GGUF:Q4_K_M --no-mmproj --jinja --reasoning-budget 0 -c 16384 --port 8080
   ```

   One model at a time. A model that pushes the machine into swap is too big for it: note `sysctl vm.swapusage`
   before the start and after the first answer. A growth over 512 MB is swap; the total alone proves nothing on a
   machine that already swaps. Without `--reasoning-budget 0` the model spends its tokens thinking.
   A first answer: `curl -s localhost:8080/v1/chat/completions -d '{"messages":[{"role":"user","content":"Say ok"}]}'`.
3. Uncomment the `local:` block under `assistant` in `jagt.yml` (copied from `jagt.desktop.yml.dist`); `model` is the name the server lists at
   `http://127.0.0.1:8080/v1/models`.

The board's head marks a server that is down or serves another model than the one configured.

## What it serves

Measured 2026-11-05 on an M4, 32 GiB (`promptEval`, 15 cases, one run; acceptance eval, 12 cases, 10-min cap):

| model | command mapping | seconds | acceptance verdicts | swap |
|---|---|---|---|---|
| cloud (haiku, sonnet) | 15/15 | 18.5 | 94 % | — |
| Qwen3.5-9B Q4_K_M | 12/15 | 2.9 | 0 of 2 in 10 min | flat |
| Qwen3.5-4B Q6_K | 11/15 | 10.8 | 10 % (before the gate) | flat |
| Qwen3.5-35B-A3B Q3_K_XL | 11/15 | 5.6 | — | +3.5 GiB: dropped |
| Qwen3.5-27B UD-IQ3_XXS (11.5 GB) | not run | — | 0 judged in 10 min (a run before it: 1 of 12, wrong, 595 s) | +3.3 GiB: dropped |
| Qwen3.5-9B Q4_K_M, `--reasoning-budget 2048` | not run | — | 0 in 10 min | -0.6 GiB (swap was 5.5 GiB before) |

A logprob confidence does not rescue the misses: Qwen3.5-9B's three wrong COMMAND_MAP answers scored 0.97–0.997 on
their enum tokens, among right ones at 0.89–0.998 (2026-11-14).

`assistant.local.kinds: [COMMAND_MAP]` is the one kind to try; a failed or late local answer is asked of the cloud
once ([0135](decisions/0135-tool-less-reads-may-call-a-local-model-directly.md)). Role reads on OpenCode
(`opencode.reads`, [0138](decisions/0138-a-read-may-run-on-opencode.md)) stay for a machine whose model passes
`roleEval -ProleCli=opencode`.

Every local model misses the same rows: "get going on ABC-99" (answers `none`), "send it for review" and the deploy row (answer `ship`).

An acceptance read on the 9B hit the 10-minute cap after 2 of 12 cases, both wrong:

- one cited a commit sha it made up;
- one answered prose outside the schema.

A second run (2026-11-07, one run per case) judged 1 of 12 in 10 min, wrong: a read the gate refused did not end
the read — the model made 7 calls — but its answer was prose outside the schema. Swap stayed flat.

A triage read on the 9B (2026-11-08, `roleEval -Prole=triage -ProleCli=opencode`, one run per case, 7m51s, swap flat)
answered 2 of 9 cases: one names who looped right, one advises a lift safe that is not. Six answers named no
`who:` line, one was unreadable. Haiku: 16 of 19 runs right, none safe where unsafe.

An acceptance read on 2026-11-08 (`roleEval -Prole=acceptance -ProleCli=opencode`, 10-min cap): the 27B IQ3_XXS judged no case.
Its calls were gated, none refused; swap 6.9 to 10.3 GiB with other work running.
The 9B with `--reasoning-budget 2048` judged no case in 10 min.

The gate worked: refused calls were retried, none discarded as unfenced. Role reads and sessions stay on the cloud.
A task session on OpenCode was not tried: a session asks more of the model than an acceptance read.

The stand runs the 9B for the ticket rewrite only: one mutation scenario passed in 3 min 16 s with swap flat.

Measured 2026-11-13 on the same machine with a container VM and one browser open, `-c 16384`, swap counted from the
model's load (a growth over 512 MB is swap):

| model | file | generate / prompt tok/s | command mapping | role reads, 10-min cap | swap |
|---|---|---|---|---|---|
| Gemma 4 26B A4B QAT Q4_0 | 14.4 GB | 32.6 / 103 | 12/15, 95 s | — | +8.5 GB: dropped |
| Gemma 4 12B QAT Q4_0 | 7.0 GB | 13.0 / 86 | 6/15, 276 s | triage 4 of 7 judged, acceptance 0 of 5 | +1.2 to +4.5 GB: dropped |
| Qwen3.5-9B Q4_K_M | 5.7 GB | 18.1 / 139 | 12/15, 119 s | triage 0 of 4, acceptance none | flat |

On 32 GiB beside a container VM, a browser and the build, a weight file of about 6 GB stays out of swap.
`--spec-type ngram-mod` gave the 12B nothing: 1 of 48 drafts accepted.

## Speed, not yet measured here

Reported 2026-11-12 for another engine on an 8 GB discrete GPU:

- Speculative decoding gave 1.3–1.6×, best on code and repeated text; llama.cpp's is `--spec-type`, an `ngram-*` type
  needing no draft model. `ngram-mod` gained nothing on a 12B here (above).
- A cached shared prefix cut time to first token from 13.3 s to 6.1 s; llama-server caches prompts by default, `--cache-reuse N` reuses chunks.
- Moving experts or the KV cache between VRAM, RAM and SSD needs a discrete GPU; unified memory gains nothing from it.

## OpenCode notes

Measured 2026-11-05, opencode 2.2.5, for a machine whose model passes the role evals:

- The default agent uses tools unasked and sends about 10k tokens on a first turn; an agent with `"*": false` tools about 700 tokens, 6 s.
- `OPENCODE_CONFIG` merges with the user's own config and with a parent `opencode.json`; `OPENCODE_DISABLE_PROJECT_CONFIG=1` drops the parents.
- Without `--standalone` a run attaches to a shared background service.
- The top-level command has no `--model` or `--agent`: the model lives in the config.
- The gate plugin is a directory with a `package.json` (`type: module`) and an `index.js` default-exporting `{id, setup}`,
  listed by absolute path under `plugin`.
- A plugin that fails to load is a WARN and the run goes on unguarded; the read then lifts as unfenced.
- A plugin stops a call by setting `effect` to `deny` and a `message` in its `permission` `evaluate` hook, before the call runs;
  a returned value is ignored, and a configured `deny` skips the hook.
