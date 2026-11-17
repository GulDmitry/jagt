/** @typedef {import("../core/store.js").Task} Task */

export const box = /** @type {HTMLInputElement} */ (
  document.getElementById("filter")
);

// Every phase clicked stays selected until clicked again: "what is not finished yet" is two of them, and a
// single-choice control cannot ask it.
/** @type {Set<string>} */
const phases = new Set();

/**
 * @param {Task} task
 * @param {string} needle
 */
const matches = (task, needle) =>
  [task.alias, task.id, task.title].some((field) =>
    (field || "").toLowerCase().includes(needle),
  );

// The counts read the set narrowed by everything EXCEPT the phase choice: one that obeyed it would read
// `build 0` the moment `review` was picked, and nothing could be clicked back.
/** @param {Task[]} tasks */
export const narrowed = (tasks) => {
  const needle = box.value.trim().toLowerCase();
  return tasks.filter((task) => !needle || matches(task, needle));
};

/** @param {Task[]} tasks */
export const shown = (tasks) =>
  narrowed(tasks).filter(
    (task) => phases.size === 0 || phases.has(task.phase ?? ""),
  );

export const on = () => (box.value.trim() ? 1 : 0) + phases.size;

/** @param {string} phase */
export const holds = (phase) => phases.has(phase);

/** @param {string} phase */
export const togglePhase = (phase) => {
  if (!phases.delete(phase)) phases.add(phase);
};

export const clearText = () => {
  const had = box.value !== "";
  box.value = "";
  return had;
};

export function clear() {
  box.value = "";
  phases.clear();
}

/** @param {() => void} repaint */
export const onChange = (repaint) => {
  box.oninput = repaint;
};
