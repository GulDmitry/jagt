import { stopLines, switchLines } from "../core/format.js";
import { t } from "../core/i18n.js";

/**
 * @typedef {import("../core/store.js").CardTask} CardTask
 * @typedef {import("../core/store.js").Action} Action
 */

export { badgeClass } from "./facts.js";

export const flagTexts = () => ({
  label: t("card.flag"),
  tip: t("card.flag.tip"),
});

/**
 * @param {Action} action
 * @param {boolean} [quiet]
 */
export const verbClass = (action, quiet = false) =>
  [
    action.primary && !action.apart && "primary",
    action.again && "again",
    quiet && "quiet",
    action.apart && "apart",
  ]
    .filter(Boolean)
    .join(" ") || undefined;

/** @param {CardTask} task */
export const actionRows = (task) => {
  /** @type {{ group?: string, items: { action: Action }[] }[]} */
  const grouped = [];
  for (const action of (task.actions || []).filter(
    (each) => each.group !== "session",
  )) {
    let row = grouped.at(-1);
    if (!row || row.group !== action.group) {
      row = { group: action.group, items: [] };
      grouped.push(row);
    }
    row.items.push({ action });
  }
  /** @param {{ action: Action }} item */
  const last = (item) => Number(Boolean(item.action.apart));
  grouped.forEach((row) =>
    row.items.sort((one, other) => last(one) - last(other)),
  );
  return grouped;
};

/** @param {CardTask} task */
export const detailClass = (task) => {
  if (task.detailKind === "PROBLEM") return "detail problem";
  return task.detailKind === "YOURS" && task.attention !== "OPTIONAL"
    ? "detail you"
    : "detail";
};

/** @param {CardTask} task */
export const titleLines = (task) => [
  ...(task.tier ? [t("card.tier.tip", { model: task.tier })] : []),
  ...switchLines(task),
];

/** @param {CardTask} task */
export const badgeTip = (task) => [task.hint, ...stopLines(task)].join("\n");
