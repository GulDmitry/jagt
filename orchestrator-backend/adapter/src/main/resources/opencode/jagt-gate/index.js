const gate = process.env.JAGT_READ_GATE;
const unjudged = "jagt could not judge this call";

function claudeCalls({ action, resources = [], metadata = {} }) {
  switch (action) {
    case "shell": return resources.map((command) => ["Bash", { command }]);
    case "read": return resources.map((file_path) => ["Read", { file_path }]);
    case "grep": return resources.map((pattern) => ["Grep", { path: metadata.path ?? ".", pattern }]);
    case "glob": return resources.map((pattern) => ["Glob", { path: metadata.root ?? ".", pattern }]);
    case "webfetch": return resources.map((url) => ["WebFetch", { url }]);
    default: return [];
  }
}

async function refusal([tool_name, tool_input]) {
  try {
    const response = await fetch(gate, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ tool_name, tool_input, cwd: process.cwd() }),
      signal: AbortSignal.timeout(5000),
    });
    if (response.status === 204) return null;
    if (response.status !== 200) return unjudged;
    const verdict = await response.json();
    return verdict.hookSpecificOutput?.permissionDecisionReason ?? unjudged;
  } catch {
    return unjudged;
  }
}

export default {
  id: "jagt-gate",
  async setup(ctx) {
    await ctx.permission.hook("evaluate", async (event) => {
      for (const call of claudeCalls(event)) {
        const reason = await refusal(call);
        if (reason) {
          event.effect = "deny";
          event.message = reason;
          return;
        }
      }
    });
  },
};
