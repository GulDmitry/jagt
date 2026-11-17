const KEY = "jagt.repliesOpened";

/** @type {{ opened: Record<string, number> }} */
const state = {
  opened: {},
};
try {
  state.opened = JSON.parse(localStorage.getItem(KEY) || "{}");
} catch {
  // storage blocked
}

/** @param {import("../core/store.js").Task} task */
export const fresh = (task) =>
  (task.repliesWrittenAt || 0) > (state.opened[task.id] || 0);

/** @param {import("../core/store.js").Task} task */
export function note(task) {
  if (!fresh(task)) return;
  state.opened[task.id] = task.repliesWrittenAt ?? 0;
  try {
    localStorage.setItem(KEY, JSON.stringify(state.opened));
  } catch {
    // storage blocked
  }
}
