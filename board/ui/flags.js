const KEY = "jagt.flags";

// Storage can be blocked or throw (a private window, cleared site data); the flags then live as long as the page.
const state = {
  flagged: new Set(),
};
try {
  state.flagged = new Set(JSON.parse(localStorage.getItem(KEY) || "[]"));
} catch {
  // storage blocked
}

export const holds = (taskId) => state.flagged.has(taskId);

export const toggle = (taskId) => {
  if (!state.flagged.delete(taskId)) state.flagged.add(taskId);
  try {
    localStorage.setItem(KEY, JSON.stringify([...state.flagged]));
  } catch {
    // storage blocked
  }
};
