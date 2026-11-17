import { stopLines, switchLines } from "../core/format.js";
import { t } from "../core/i18n.js";

/**
 * @typedef {import("../core/store.js").CardTask} CardTask
 * @typedef {import("../core/store.js").Action} Action
 */

export const flagTexts = () => ({
  label: t("card.flag"),
  tip: t("card.flag.tip"),
});

// A verb drawn apart stays muted at the row's end even where the move names it.
/** @param {Action} action */
export const verbClass = (action) =>
  [
    action.primary && !action.apart && "primary",
    action.again && "again",
    action.apart && "apart",
  ]
    .filter(Boolean)
    .join(" ") || undefined;

// Which groups exist, and which comes first, stays the projection's answer; a verb drawn apart ends its row.
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

// A problem is broken whatever the tier says; a move of theirs drops its colour with the tier.
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
