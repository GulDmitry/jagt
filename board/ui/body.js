import { render as paint } from "preact";

import { list } from "./list.js";
import { pane } from "./pane.js";
import * as selection from "./selection.js";
import { hideStaleTip } from "./tips.js";

/** @typedef {import("./view.js").View} View */

const board = /** @type {HTMLElement} */ (document.getElementById("board"));

/**
 * @param {View} view
 * @param {() => void} repaint
 */
export function body(view, repaint) {
  const { open } = view;
  board.classList.toggle("open", selection.isOpen());
  paint(
    open ? [list({ ...view, open }), pane({ ...view, open }, repaint)] : null,
    board,
  );
  hideStaleTip(null);
}
