import { list } from "./list.js";
import { paint } from "./paint.js";
import * as rolelogpane from "./rolelogpane.js";
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
  const role = view.logStands;
  board.classList.toggle("open", selection.isOpen() || role);
  paint(
    open || role
      ? [
          view.shown.length ? list({ ...view, open }) : null,
          rolelogpane.stand({ ...view, open }, repaint),
        ]
      : null,
    board,
  );
  hideStaleTip(null);
}
