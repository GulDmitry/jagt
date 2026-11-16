import { render as paint } from "preact";
import { list } from "./list.js";
import { pane } from "./pane.js";
import * as selection from "./selection.js";
import { hideStaleTip } from "./tips.js";

const board = document.getElementById("board");

export function body(view, repaint) {
  board.classList.toggle("open", selection.isOpen());
  paint(view.open ? [list(view), pane(view, repaint)] : null, board);
  hideStaleTip(null);
}
