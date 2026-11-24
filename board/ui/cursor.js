/** @typedef {import("../core/store.js").Task} Task */

/**
 * @param {Task[]} shown
 * @param {string | null} chosen
 * @param {string | null} linked
 */
export const current = (shown, chosen, linked) =>
  shown.find((task) => task.id === chosen) ||
  (chosen === null &&
    linked !== null &&
    shown.find((task) => task.alias === linked || task.id === linked)) ||
  shown.find((task) => task.attention === "REQUIRED") ||
  shown[0] ||
  null;

/**
 * @param {Task[]} ordered
 * @param {string | undefined} at
 * @param {number} by
 */
export const stepTarget = (ordered, at, by) => {
  const from = ordered.findIndex((task) => task.id === at);
  return ordered[Math.min(ordered.length - 1, Math.max(0, from + by))]?.id;
};

/**
 * @param {Task[]} ordered
 * @param {string | undefined} at
 */
export const nextRequiredTarget = (ordered, at) => {
  const from = ordered.findIndex((task) => task.id === at);
  return [...ordered.slice(from + 1), ...ordered.slice(0, from + 1)].find(
    (task) => task.attention === "REQUIRED",
  )?.id;
};
