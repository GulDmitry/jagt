import * as flags from "./flags.js";
import { render } from "./render.js";
import { press } from "./say.js";
import * as selection from "./selection.js";
import * as tabs from "./tabs.js";

const board = document.getElementById("board");

// A click carries NAMES, never a captured task: a rebuilt row cannot act for the old one.
export const onClick = ({ action }) => {
  board.onclick = (event) => {
    if (event.target.closest("button[data-back]")) {
      selection.back();
      render();
      return;
    }
    const tab = event.target.closest("button[data-tab]");
    if (tab) {
      tabs.choose(tab.dataset.tab);
      render();
      return;
    }
    const named = event.target.closest("button[data-window]");
    if (named) {
      tabs.pickWindow(named.dataset.window || null);
      render();
      return;
    }
    const file = event.target.closest("button[data-file]");
    if (file) {
      tabs.pick(file.dataset.task, file.dataset.file);
      render();
      return;
    }
    const picked = event.target.closest("button[data-select]");
    if (picked) {
      selection.choose(picked.dataset.select);
      render();
      return;
    }
    const flagged = event.target.closest("button[data-flag]");
    if (flagged) {
      flags.toggle(flagged.dataset.flag);
      render();
      return;
    }
    const key = event.target.closest("button[data-press]");
    if (key) {
      press(key.dataset.task, key.dataset.press);
      return;
    }
    const button = event.target.closest("button[data-action]");
    if (button) action(button.dataset.task, button.dataset.action);
  };
};
