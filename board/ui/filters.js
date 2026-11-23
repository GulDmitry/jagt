import { byPhases, byText } from "./taskfilter.js";

/** @typedef {import("../core/store.js").Task} Task */

export const box = /** @type {HTMLInputElement} */ (
  document.getElementById("filter")
);

/** @type {Set<string>} */
const phases = new Set();

// The counts read the set narrowed by everything EXCEPT the phase choice: one that obeyed it would read
// `build 0` the moment `review` was picked, and nothing could be clicked back.
/** @param {Task[]} tasks */
export const narrowed = (tasks) => byText(tasks, box.value);

/** @param {Task[]} tasks */
export const shown = (tasks) => byPhases(narrowed(tasks), phases);

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
