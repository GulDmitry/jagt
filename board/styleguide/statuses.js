import { card, tools, verb } from "./cards.js";

const STATUSES = [
  ["NEW", "starting", "AGENT", ""],
  ["PLAN_PENDING", "plan waiting", "YOU", "read the plan"],
  ["IN_PROGRESS", "agent working", "AGENT", ""],
  ["VERIFYING", "verifying", "AGENT", ""],
  ["REVIEW_PENDING", "not shipped", "YOU", "review and ship"],
  ["SHIPPING", "pushing", "AGENT", ""],
  ["CI_POLLING", "out for review", "CI", ""],
  ["CI_FAILED", "checks failed", "YOU", "relay the failed checks"],
  ["REVIEWED", "awaiting approval", "CI", ""],
  ["APPROVED", "approved", "YOU", "you can deploy it"],
  ["DEPLOY_CONFLICT", "deploy conflict", "YOU", "resolve the conflict"],
  ["DEPLOYED", "deployed", "NONE", ""],
  ["REVERTED", "reverted", "YOU", "you can ship a fix"],
  ["DONE", "done", "NONE", ""],
];

export const statusCards = STATUSES.map(([status, label, owner, ask], at) =>
  card(`${status}: ${label}`, {
    id: `ABC-${100 + at}`,
    alias: `s${at + 1}`,
    title: `A task ${label}`,
    owner,
    attention: ask.startsWith("you can ")
      ? "OPTIONAL"
      : ask
        ? "REQUIRED"
        : "NONE",
    ask: ask || undefined,
    hint: ask,
    status,
    statusLabel: label,
    actions: [verb("done", "Done", "flow"), ...tools],
  }),
);
