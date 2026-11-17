import { closest } from "../core/dom.js";
import * as flags from "./flags.js";
import { press } from "./say.js";
import * as selection from "./selection.js";
import * as tabs from "./tabs.js";

/** @typedef {HTMLElement | SVGElement} Hit */
/** @typedef {[selector: string, handle: (hit: Hit) => void, repaints: boolean]} Route */

const board = /** @type {HTMLElement} */ (document.getElementById("board"));

/**
 * @param {Hit} hit
 * @param {string} name
 */
const datum = (hit, name) => hit.dataset[name] ?? "";

/** @type {Route[]} */
const routes = [
  ["button[data-back]", () => selection.back(), true],
  ["button[data-tab]", (hit) => tabs.choose(datum(hit, "tab")), true],
  [
    "button[data-window]",
    (hit) => tabs.pickWindow(hit.dataset.window || null),
    true,
  ],
  [
    "button[data-file]",
    (hit) => tabs.pick(datum(hit, "task"), datum(hit, "file")),
    true,
  ],
  [
    "button[data-select]",
    (hit) => selection.choose(datum(hit, "select")),
    true,
  ],
  ["button[data-flag]", (hit) => flags.toggle(datum(hit, "flag")), true],
  [
    "button[data-press]",
    (hit) => press(datum(hit, "task"), datum(hit, "press")),
    false,
  ],
];

// A click carries NAMES, never a captured task: a rebuilt row cannot act for the old one.
/** @param {{ action: (taskId: string, actionId: string) => void, repaint: () => void }} handlers */
export const onClick = ({ action, repaint }) => {
  /** @type {Route[]} */
  const all = [
    ...routes,
    [
      "button[data-action]",
      (hit) => action(datum(hit, "task"), datum(hit, "action")),
      false,
    ],
  ];
  board.onclick = (event) => {
    for (const [selector, handle, repaints] of all) {
      const hit = closest(event, selector);
      if (!hit) continue;
      handle(hit);
      if (repaints) repaint();
      return;
    }
  };
};
