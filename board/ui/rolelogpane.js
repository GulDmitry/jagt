import { RoleLog } from "../components/RoleLog.js";
import { html } from "../components/html.js";
import { pane } from "./pane.js";
import * as rolelog from "./rolelog.js";

/** @typedef {import("./view.js").View} View */

export const showing = () => rolelog.open() !== null;

// An open role's log stands in the pane's place.
/**
 * @param {View} view
 * @param {() => void} repaint
 */
export const stand = (view, repaint) => {
  const role = rolelog.open();
  return role
    ? html`<${RoleLog} role=${role} lines=${rolelog.lines()} />`
    : pane(view, repaint);
};
