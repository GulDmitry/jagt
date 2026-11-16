const KEY = "jagt.repliesOpened";

let opened = {};
try {
  opened = JSON.parse(localStorage.getItem(KEY) || "{}");
} catch {
  // storage blocked
}

export const fresh = (task) =>
  (task.repliesWrittenAt || 0) > (opened[task.id] || 0);

export function note(task) {
  if (!fresh(task)) return;
  opened[task.id] = task.repliesWrittenAt;
  try {
    localStorage.setItem(KEY, JSON.stringify(opened));
  } catch {
    // storage blocked
  }
}
