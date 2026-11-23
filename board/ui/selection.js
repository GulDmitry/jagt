import { current as pick, nextRequiredTarget, stepTarget } from "./cursor.js";
import * as rolelog from "./rolelog.js";

/** @typedef {import("../core/store.js").Task} Task */

/** @type {{ chosen: string | null, linked: string | null, opened: boolean }} */
const state = {
  chosen: null,
  linked: null,
  opened: false,
};

/** @param {string} taskId */
export const choose = (taskId) => {
  rolelog.close();
  state.chosen = taskId;
  state.opened = true;
};

// A task a link names, by alias or id, stays open until the human picks another.
/** @param {string} name */
export const link = (name) => {
  rolelog.close();
  state.linked = name;
  state.opened = true;
};

export const back = () => {
  rolelog.close();
  state.opened = false;
};

export const isOpen = () => state.opened;

const narrow = window.matchMedia("(max-width: 1199px)");

// Wide, the open task always stands beside the list; narrow, only once the human opened it.
export const paneShown = () => !narrow.matches || state.opened;

/** @param {Task[]} shown */
export const current = (shown) => pick(shown, state.chosen, state.linked);

// A walk moves the cursor through the list; on a narrow window Enter is what opens the task it rests on.
/**
 * @param {Task[]} ordered
 * @param {number} by
 */
export const step = (ordered, by) => {
  const next = stepTarget(ordered, current(ordered)?.id, by);
  if (next) state.chosen = next;
};

// A row the keyboard moved the cursor to is also where the focus goes, so the next key starts from it.
export const focusSelected = () => {
  const pick = document.querySelector("#tasks .row.selected .pick");
  if (pick instanceof HTMLElement) pick.focus();
};

/** @param {Task[]} ordered */
export const nextRequired = (ordered) => {
  const waiting = nextRequiredTarget(ordered, current(ordered)?.id);
  if (waiting) state.chosen = waiting;
};
