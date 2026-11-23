const KEY = "jagt.seen";

/** @type {{ seen: Record<string, number> }} */
const state = {
  seen: {},
};
try {
  state.seen = JSON.parse(localStorage.getItem(KEY) || "{}");
} catch {
  // storage blocked
}

const save = () => {
  try {
    localStorage.setItem(KEY, JSON.stringify(state.seen));
  } catch {
    // storage blocked
  }
};

/**
 * @param {import("../core/store.js").Task[]} tasks
 * @param {import("../core/store.js").Task | null} open
 */
export function note(tasks, open) {
  if (!tasks.length) return;
  const next = Object.fromEntries(
    tasks.map((task) => [task.id, state.seen[task.id] ?? task.statusSince]),
  );
  if (open) next[open.id] = open.statusSince;
  const changed = JSON.stringify(next) !== JSON.stringify(state.seen);
  state.seen = next;
  if (changed) save();
}

/** @param {import("../core/store.js").Task} task */
export const changed = (task) =>
  state.seen[task.id] !== undefined && state.seen[task.id] !== task.statusSince;
