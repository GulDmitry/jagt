const state = {
  chosen: null,
  linked: null,
  opened: false,
};

export const choose = (taskId) => {
  state.chosen = taskId;
  state.opened = true;
};

// A task a link names, by alias or id, stays open until the human picks another.
export const link = (name) => {
  state.linked = name;
  state.opened = true;
};

export const back = () => {
  state.opened = false;
};

export const isOpen = () => state.opened;

const narrow = window.matchMedia("(max-width: 1199px)");

// Wide, the open task always stands beside the list; narrow, only once the human opened it.
export const paneShown = () => !narrow.matches || state.opened;

// A choice that left the view falls back to the first task waiting on the human, so the pane is never empty.
export const current = (shown) =>
  shown.find((task) => task.id === state.chosen) ||
  (state.chosen === null &&
    state.linked !== null &&
    shown.find(
      (task) => task.alias === state.linked || task.id === state.linked,
    )) ||
  shown.find((task) => task.attention === "REQUIRED") ||
  shown[0] ||
  null;

// A walk moves the cursor through the list; on a narrow window Enter is what opens the task it rests on.
export const step = (ordered, by) => {
  const at = ordered.findIndex((task) => task.id === current(ordered)?.id);
  const next = ordered[Math.min(ordered.length - 1, Math.max(0, at + by))];
  if (next) state.chosen = next.id;
};

// A row the keyboard moved the cursor to is also where the focus goes, so the next key starts from it.
export const focusSelected = () =>
  document.querySelector("#tasks .row.selected .pick")?.focus();

export const nextRequired = (ordered) => {
  const at = ordered.findIndex((task) => task.id === current(ordered)?.id);
  const waiting = [...ordered.slice(at + 1), ...ordered.slice(0, at + 1)].find(
    (task) => task.attention === "REQUIRED",
  );
  if (waiting) state.chosen = waiting.id;
};
