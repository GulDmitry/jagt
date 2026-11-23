import { body } from "./body.js";
import { chrome } from "./chrome.js";
import * as rolelog from "./rolelog.js";
import * as rolelogchips from "./rolelogchips.js";
import { view } from "./view.js";

const board = /** @type {HTMLElement} */ (document.getElementById("board"));

export function render() {
  const now = view();
  chrome(now);
  rolelogchips.render();
  body(now, render);
}

rolelog.onChange(render);

/** @param {{ task: string, action: string }} pressed */
const buttonOf = ({ task, action }) => {
  const found = board.querySelector(
    `#pane button[data-task="${CSS.escape(task)}"][data-action="${CSS.escape(action)}"]`,
  );
  return found instanceof HTMLElement ? found : null;
};

/** @param {{ task: string, action: string }} pressed */
export function refocus(pressed) {
  if (document.activeElement === document.body) buttonOf(pressed)?.focus();
}
