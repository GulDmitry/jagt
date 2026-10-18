// Which task the pane shows, and whether a narrow window shows the pane instead of the list.

let chosen = null;
let linked = null;
let opened = false;

export const choose = (taskId) => {
  chosen = taskId;
  opened = true;
};

// A task a link names, by alias or id, stays open until the human picks another.
export const link = (name) => {
  linked = name;
  opened = true;
};

export const back = () => {
  opened = false;
};

export const isOpen = () => opened;

const narrow = window.matchMedia('(max-width: 1199px)');

// Wide, the open task always stands beside the list; narrow, only once the human opened it.
export const paneShown = () => !narrow.matches || opened;

// A choice that left the view falls back to the first task waiting on the human, so the pane is never empty.
export const current = (shown) => shown.find((task) => task.id === chosen)
  || (chosen === null && linked !== null && shown.find((task) => task.alias === linked || task.id === linked))
  || shown.find((task) => task.attention === 'REQUIRED') || shown[0] || null;

// A walk moves the cursor through the list; on a narrow window Enter is what opens the task it rests on.
export const step = (ordered, by) => {
  const at = ordered.findIndex((task) => task.id === current(ordered)?.id);
  const next = ordered[Math.min(ordered.length - 1, Math.max(0, at + by))];
  if (next) chosen = next.id;
};

// A row the keyboard moved the cursor to is also where the focus goes, so the next key starts from it.
export const focusSelected = () => document.querySelector('#tasks .row.selected .pick')?.focus();

export const nextRequired = (ordered) => {
  const at = ordered.findIndex((task) => task.id === current(ordered)?.id);
  const waiting = [...ordered.slice(at + 1), ...ordered.slice(0, at + 1)].find((task) => task.attention === 'REQUIRED');
  if (waiting) chosen = waiting.id;
};
