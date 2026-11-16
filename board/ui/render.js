// One repaint of everything: a row changing in place keeps its position.

import { body } from "./body.js";
import { chrome } from "./chrome.js";
import { view } from "./view.js";

const board = document.getElementById("board");

export function render() {
  const now = view();
  chrome(now);
  body(now, render);
}

const buttonOf = ({ task, action }) =>
  board.querySelector(
    `#pane button[data-task="${CSS.escape(task)}"][data-action="${CSS.escape(action)}"]`,
  );

// A press disables its button, dropping focus to the page; nobody else took it.
export function refocus(pressed) {
  if (document.activeElement === document.body) buttonOf(pressed)?.focus();
}
