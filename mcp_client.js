#!/usr/bin/env node
/**
 * MCP stdio-to-HTTP bridge, agent-agnostic: forwards newline-delimited JSON-RPC to the backend and injects
 * `X-Working-Directory`, which is how the backend knows WHICH worktree is calling. The config declaring this
 * proxy is per-vendor and lives in that `AgentRuntime`, never here.
 *
 * Symlinked into every task worktree, so `process.cwd()` is the worktree for a sub-agent and the orchestrator
 * root for Master.
 */
const readline = require('node:readline');

// 127.0.0.1, not localhost: the server binds IPv4 loopback, and `localhost` resolves ::1 first on macOS —
// which costs a refused connection per call, or every call on a Node without happy-eyeballs.
const SERVER_URL = process.env.MCP_SERVER_URL || 'http://127.0.0.1:8290/mcp';
const CWD = process.cwd();

// A backend restart must not kill the session's MCP connection: agents tend to
// mark the server as failed on the first error, so retry transient connection
// failures with backoff (~15s total) before giving up.
const RETRY_DELAYS_MS = [500, 1000, 2000, 4000, 8000];

async function postWithRetry(body) {
  for (let attempt = 0; ; attempt++) {
    try {
      return await fetch(SERVER_URL, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'X-Working-Directory': CWD,
        },
        body,
      });
    } catch (err) {
      // Only ECONNREFUSED is provably safe to retry: the request never reached
      // the server. A mid-flight reset may have already executed a
      // non-idempotent tool (initialize_task) — surface it instead of re-POSTing.
      const code = err.cause && err.cause.code;
      if (code !== 'ECONNREFUSED' || attempt >= RETRY_DELAYS_MS.length) throw err;
      await new Promise((resolve) => setTimeout(resolve, RETRY_DELAYS_MS[attempt]));
    }
  }
}

const rl = readline.createInterface({ input: process.stdin, terminal: false });

rl.on('line', async (line) => {
  line = line.trim();
  if (!line) return;

  let message;
  try {
    message = JSON.parse(line);
  } catch {
    return; // not JSON-RPC, ignore
  }

  const answer = (payload) => {
    // Requests (with id) must get an answer or the agent hangs forever.
    if (message.id !== undefined && message.id !== null) {
      process.stdout.write(JSON.stringify({ jsonrpc: '2.0', id: message.id, ...payload }) + '\n');
    }
  };

  try {
    const response = await postWithRetry(line);
    const text = (await response.text()).trim();
    if (!response.ok) {
      // A Spring error page is not a JSON-RPC message — never forward it raw.
      answer({ error: { code: -32000, message: `backend returned HTTP ${response.status}: ${text.slice(0, 300)}` } });
      return;
    }
    // Empty body = notification, nothing to write back.
    if (text) {
      process.stdout.write(text + '\n');
    }
  } catch (err) {
    answer({ error: { code: -32000, message: `orchestrator backend unreachable at ${SERVER_URL}: ${err.message}` } });
  }
});
