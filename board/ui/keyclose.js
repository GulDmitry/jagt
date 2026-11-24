import * as filters from "./filters.js";
import * as menu from "./menu.js";
import * as palette from "./palette.js";
import { render } from "./render.js";
import * as selection from "./selection.js";

/** @param {KeyboardEvent} event */
export function escapeKey(event) {
  if (event.key !== "Escape") return false;
  const closed =
    palette.close() ||
    menu.close() ||
    (event.target === filters.box && filters.clearText());
  if (!closed) selection.back();
  render();
  return true;
}
