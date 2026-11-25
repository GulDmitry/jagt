/** @typedef {import("../core/store.js").Task} Task */

// An alias-less task sorts after every aliased one, as `-` reads on the card. `numeric` is what puts p2 before p10.
/**
 * @param {Task} a
 * @param {Task} b
 */
export const compare = (a, b) =>
  (a.alias ? 0 : 1) - (b.alias ? 0 : 1) ||
  (a.alias || "").localeCompare(b.alias || "", "en", { numeric: true });

/** @param {Task[]} tasks */
export const byAlias = (tasks) => [...tasks].sort(compare);
