# Local models

[← README](../README.md)

Opt-in: the cloud stays the default. A local model serves the reads that need no tool; role reads and sessions
stay on the cloud until a model passes their evals here.

## Quick start

| memory | model | file |
|---|---|---|
| 16 GiB | `unsloth/Qwen3.5-4B-GGUF:Q6_K` | 3.5 GB |
| 24 GiB and more | `unsloth/Qwen3.5-9B-GGUF:Q4_K_M` | 5.7 GB |

1. `brew install llama.cpp` (Linux: a release build of [llama.cpp](https://github.com/ggml-org/llama.cpp)).
2. Start the server with the model for your memory; the first run downloads it:

   ```sh
   llama-server -hf unsloth/Qwen3.5-9B-GGUF:Q4_K_M --no-mmproj --jinja --reasoning-budget 0 -c 16384 --port 8080
   ```

   One model at a time. A model that pushes the machine into swap is too big for it: check `sysctl vm.swapusage`
   before and after its first answer. Without `--reasoning-budget 0` the model spends its tokens thinking.
3. Uncomment the `local:` block under `assistant` in `jagt.yml`; `model` is the name the server lists at
   `http://127.0.0.1:8080/v1/models`.

The board's head marks a server that is down or serves another model than the one configured.

## What it serves

Measured 2026-11-05 on an M4, 32 GiB (`promptEval`, 15 cases, one run; QA eval, 12 cases, 10-min cap):

| model | command mapping | seconds | QA verdicts | swap |
|---|---|---|---|---|
| cloud (haiku, sonnet) | 15/15 | 18.5 | 94 % | — |
| Qwen3.5-9B Q4_K_M | 12/15 | 2.9 | 0 of 2 in 10 min | flat |
| Qwen3.5-4B Q6_K | 11/15 | 10.8 | 10 % (before the gate) | flat |
| Qwen3.5-35B-A3B Q3_K_XL | 11/15 | 5.6 | — | +3.5 GiB: dropped |

`assistant.local.kinds: [COMMAND_MAP]` is the one kind to try; a failed or late local answer is asked of the cloud
once ([0135](decisions/0135-tool-less-reads-may-call-a-local-model-directly.md)). Role reads on OpenCode
(`opencode.reads`, [0138](decisions/0138-a-read-may-run-on-opencode.md)) stay for a machine whose model passes
`roleEval -ProleCli=opencode`.
