// Which task the pane shows, and whether a narrow window shows the pane instead of the list.

let chosen = null;
let opened = false;

export const choose = (taskId) => {
  chosen = taskId;
  opened = true;
};

export const back = () => {
  opened = false;
};

export const isOpen = () => opened;

// A choice that left the view falls back to the first task waiting on the human, so the pane is never empty.
export const current = (shown) => shown.find((task) => task.id === chosen)
  || shown.find((task) => task.attention === 'REQUIRED') || shown[0] || null;

export const step = (ordered, by) => {
  const at = ordered.findIndex((task) => task.id === current(ordered)?.id);
  const next = ordered[Math.min(ordered.length - 1, Math.max(0, at + by))];
  if (next) chosen = next.id;
};

export const nextRequired = (ordered) => {
  const at = ordered.findIndex((task) => task.id === current(ordered)?.id);
  const waiting = [...ordered.slice(at + 1), ...ordered.slice(0, at + 1)].find((task) => task.attention === 'REQUIRED');
  if (waiting) chosen = waiting.id;
};
