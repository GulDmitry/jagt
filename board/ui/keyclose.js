import * as filters from "./filters.js";
import * as menu from "./menu.js";
import * as palette from "./palette.js";
import { render } from "./render.js";
import * as selection from "./selection.js";

// A <dialog> closes on Escape by itself; the inline forms do not. One press closes the innermost thing open.
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
