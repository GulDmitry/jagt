// Which status of each task the human last had open. This browser's alone, like the flags.

const KEY = 'jagt.seen';

let seen = {};
try {
  seen = JSON.parse(localStorage.getItem(KEY) || '{}');
} catch {
  // An unreadable store reads as everything seen.
}

const save = () => {
  try {
    localStorage.setItem(KEY, JSON.stringify(seen));
  } catch {
    // Kept for this page only.
  }
};

// A task met for the first time counts as seen: a fresh browser must not mark every row as changed. An empty
// board forgets nothing, since the page paints one before the tasks arrive.
export function note(tasks, open) {
  if (!tasks.length) return;
  const next = Object.fromEntries(tasks.map((task) => [task.id, seen[task.id] ?? task.statusSince]));
  if (open) next[open.id] = open.statusSince;
  const changed = JSON.stringify(next) !== JSON.stringify(seen);
  seen = next;
  if (changed) save();
}

export const changed = (task) => seen[task.id] !== undefined && seen[task.id] !== task.statusSince;
