/** @typedef {import("../core/store.js").Task} Task */

/**
 * @param {Task} task
 * @param {string} needle
 */
const matches = (task, needle) =>
  [task.alias, task.id, task.title].some((field) =>
    (field || "").toLowerCase().includes(needle),
  );

/**
 * @param {Task[]} tasks
 * @param {string} text
 */
export const byText = (tasks, text) => {
  const needle = text.trim().toLowerCase();
  return tasks.filter((task) => !needle || matches(task, needle));
};

/**
 * @param {Task[]} tasks
 * @param {ReadonlySet<string>} phases
 */
export const byPhases = (tasks, phases) =>
  tasks.filter((task) => phases.size === 0 || phases.has(task.phase ?? ""));
