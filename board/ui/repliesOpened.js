const KEY = "jagt.repliesOpened";

const state = {
  opened: {},
};
try {
  state.opened = JSON.parse(localStorage.getItem(KEY) || "{}");
} catch {
  // storage blocked
}

export const fresh = (task) =>
  (task.repliesWrittenAt || 0) > (state.opened[task.id] || 0);

export function note(task) {
  if (!fresh(task)) return;
  state.opened[task.id] = task.repliesWrittenAt;
  try {
    localStorage.setItem(KEY, JSON.stringify(state.opened));
  } catch {
    // storage blocked
  }
}
