import { countdown } from "../core/format.js";
import { t } from "../core/i18n.js";
import { full } from "../core/time.js";

/**
 * @typedef {import("../core/store.js").Task} CardTask
 * @typedef {import("../core/store.js").Watch} Watch
 * @typedef {{ line: string, stalled?: boolean }} Stalled
 */

/**
 * @param {Watch | null | undefined} watch
 * @param {number} now
 * @returns {Stalled | null}
 */
export const watchLine = (watch, now) => {
  if (!watch || !watch.note) return null;
  if (watch.state === "WATCHING") {
    const remaining = watch.nextPollAt - now;
    return {
      line: t("meta.next-poll", {
        when: remaining <= 0 ? t("due") : countdown(remaining),
      }),
    };
  }
  return { line: `${watch.label} — ${watch.note}`, stalled: true };
};

// Equality rather than a negation: a projection missing the field would throw inside the render and blank the board.
/** @param {CardTask} task */
export const marked = (task) =>
  task.pipeline === "RED" ||
  task.pipeline === "RUNNING" ||
  task.pipeline === "GREEN";

/** @param {...(string | false | null | undefined)} names */
export const classes = (...names) => names.filter(Boolean).join(" ");

/**
 * @param {CardTask} task
 * @param {number} openedAt
 * @param {Stalled | null} watch
 */
export const requestLines = (task, openedAt, watch) => {
  const lines = [
    openedAt > 0
      ? t("meta.request.opened", { at: full(openedAt) })
      : t("meta.request.undated"),
  ];
  if (task.approved != null)
    lines.push(t(task.approved ? "meta.approved" : "meta.not-approved"));
  const ships = (task.history || []).filter(
    (step) => step.status === "SHIPPING",
  ).length;
  if (ships > 1) lines.push(t("meta.shipped", { n: ships }));
  if (!marked(task))
    lines.push(
      t("meta.checks", { said: task.pipelineSaid || t("meta.checks.unread") }),
    );
  if (watch) lines.push(watch.line);
  return lines;
};

/**
 * @param {string} tip
 * @param {string} next
 */
const joined = (tip, next) => (tip ? `${tip}\n\n${next}` : next);

/**
 * @param {CardTask} task
 * @param {Stalled | null} stalled
 */
export const statusTip = (task, stalled) => {
  let tip = stalled ? stalled.line : "";
  if (task.waiting) tip = joined(tip, task.waiting);
  const live =
    task.deployed && !(task.actions || []).some((action) => action.again);
  if (live) tip = joined(tip, t("meta.live"));
  return { tip, live };
};
