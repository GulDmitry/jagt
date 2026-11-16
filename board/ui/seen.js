const KEY = "jagt.seen";

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

// A task met for the first time counts as seen: a fresh browser must not mark every row as changed. An empty
// board forgets nothing, since the page paints one before the tasks arrive.
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

export const changed = (task) =>
  state.seen[task.id] !== undefined && state.seen[task.id] !== task.statusSince;
