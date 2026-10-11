// Which tasks the human flagged as important. This browser's alone: the server never hears of it.

const KEY = 'jagt.flags';

// Storage can be blocked or throw (a private window, cleared site data); the flags then live as long as the page.
let flagged = new Set();
try {
  flagged = new Set(JSON.parse(localStorage.getItem(KEY) || '[]'));
} catch {
  // An unreadable store reads as nothing flagged.
}

export const holds = (taskId) => flagged.has(taskId);

export const toggle = (taskId) => {
  if (!flagged.delete(taskId)) flagged.add(taskId);
  try {
    localStorage.setItem(KEY, JSON.stringify([...flagged]));
  } catch {
    // Kept for this page only.
  }
};
