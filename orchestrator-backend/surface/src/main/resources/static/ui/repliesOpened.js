// Which write of each task's replies the human last had its tab open on. This browser's alone, like the flags.

const KEY = 'jagt.repliesOpened';

let opened = {};
try {
  opened = JSON.parse(localStorage.getItem(KEY) || '{}');
} catch {
  // An unreadable store reads as nothing opened.
}

export const fresh = (task) => (task.repliesWrittenAt || 0) > (opened[task.id] || 0);

export function note(task) {
  if (!fresh(task)) return;
  opened[task.id] = task.repliesWrittenAt;
  try {
    localStorage.setItem(KEY, JSON.stringify(opened));
  } catch {
    // Kept for this page only.
  }
}
