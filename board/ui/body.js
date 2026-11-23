import { render as paint } from "preact";

import { list } from "./list.js";
import { RoleLog } from "../components/RoleLog.js";
import { html } from "../components/html.js";
import { pane } from "./pane.js";
import * as rolelog from "./rolelog.js";
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
  const role = rolelog.open();
  board.classList.toggle("open", selection.isOpen() || role !== null);
  paint(
    open || role
      ? [
          list({ ...view, open }),
          role
            ? html`<${RoleLog} role=${role} lines=${rolelog.lines()} />`
            : pane({ ...view, open }, repaint),
        ]
      : null,
    board,
  );
  hideStaleTip(null);
}
