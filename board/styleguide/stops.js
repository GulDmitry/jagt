import { card, tools, verb } from "./cards.js";

const STOPS = [
  [
    "STALLED",
    "GUARD",
    "rounds bring nothing new",
    "Give the session what the rounds lacked; if nothing was missing, report a jagt defect",
  ],
  [
    "SHIPS",
    "GUARD",
    "ships with no new note",
    "Check a new note or commit waits to ship; if one does, report a jagt defect",
  ],
  [
    "MISS",
    "GUARD",
    "no case for this step",
    "Set off by jagt or the Master: report a jagt defect; by a session: add a case if one should send it",
  ],
  [
    "ROUNDS",
    "GUARD",
    "same round sent too often",
    "Answer the session's question, or give it what it lacked",
  ],
  [
    "PACE",
    "GUARD",
    "too many unattended moves in an hour",
    "Read the last hour's moves: say what each one changed",
  ],
  [
    "CHECKS",
    "BROKEN",
    "checks cannot be read",
    "Check that the code host's MCP can list the pipelines",
  ],
  [
    "UNDELIVERED",
    "BROKEN",
    "a step never landed",
    "Check that the session's window is running",
  ],
  [
    "UNCHANGED_FAILURE",
    "GUARD",
    "same failure again",
    "Change the tree, or fix the check itself: its access, a file it needs, or a probe that execs into a stack no earlier probe starts",
  ],
  [
    "INFRA",
    "BROKEN",
    "checks failed on infrastructure",
    "Re-run the job or fix the runner: the next read of the checks clears it",
  ],
  [
    "WAIT",
    "BROKEN",
    "a dependency stayed down",
    "Bring up what the session named: its next report clears it",
  ],
  [
    "ACCEPTANCE_FAILED",
    "GUARD",
    "the acceptance check failed",
    "Fix what the check needs, then lift: the same check runs once more",
  ],
  [
    "ACCEPTANCE_UNREAD",
    "BROKEN",
    "acceptance lacks an input",
    "Make the missing input readable: jagt's next read of it clears the stop",
  ],
  [
    "DEPLOY_UNREAD",
    "BROKEN",
    "deploy pipeline cannot be read",
    "Check that the code host's CLI can list the deploy commit's pipelines",
  ],
  ["PAUSE", "PAUSE", "you paused it", "Look at what you paused it for"],
];

export const stopCards = STOPS.map(([trigger, kind, reason, next], at) =>
  card(`${trigger} (${kind}): ${reason}`, {
    id: `ABC-${200 + at}`,
    alias: `p${at + 1}`,
    title: `A task stopped: ${reason}`,
    owner: "YOU",
    attention: kind === "PAUSE" ? "OPTIONAL" : "REQUIRED",
    ask:
      kind === "PAUSE"
        ? "you can lift the pause"
        : ["lift the stop", reason].join(" — "),
    hint:
      kind === "PAUSE"
        ? "you paused this task; lift your pause to let jagt move it again"
        : "the task is stopped; hover for why and what to check, then lift or close",
    status: "IN_PROGRESS",
    statusLabel: kind === "PAUSE" ? "‖ paused" : "agent working",
    stop: { kind, reason, at: Date.now(), evidence: reason, next },
    actions: [
      verb("lift", "Lift stop", "flow", { primary: true }),
      verb("done", "Done", "flow"),
      ...tools,
    ],
  }),
);
