#!/usr/bin/env node
/**
 * MCP stdio-to-HTTP proxy bridge.
 *
 * Claude Code speaks MCP over stdio (newline-delimited JSON-RPC). This script
 * forwards every message to the Spring Boot orchestrator and injects the
 * caller's working directory as the X-Working-Directory header — that is how
 * the backend knows WHICH agent (worktree/task) is calling it.
 *
 * This file is symlinked into every task worktree by initialize_task, so
 * process.cwd() is the worktree path for sub-agents and the orchestrator
 * root for the Master session.
 */
const readline = require('node:readline');

const SERVER_URL = process.env.MCP_SERVER_URL || 'http://localhost:8290/mcp';
const CWD = process.cwd();

// A backend restart must not kill the session's MCP connection: Claude Code
// marks the server as failed on the first error, so retry transient
// connection failures with backoff (~15s total) before giving up.
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
    // Requests (with id) must get an answer or Claude hangs forever.
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
