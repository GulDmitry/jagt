import { note } from "./interactions.js";
import * as launch from "./launch.js";
import { render } from "./render.js";
import * as selection from "./selection.js";
import { ordered } from "./view.js";

/**
 * @param {Event} event
 * @param {string} selector
 */
const inside = (event, selector) =>
  event.target instanceof Element && event.target.closest(selector);

// A letter moves the selection or opens a form.
/** @type {Record<string, () => void>} */
export const letters = {
  t: () => launch.open(),
  j: () => selection.step(ordered(), 1),
  k: () => selection.step(ordered(), -1),
  n: () => selection.nextRequired(ordered()),
};

/**
 * @param {KeyboardEvent} event
 * @param {boolean} plain
 */
export function letterKey(event, plain) {
  const letter = plain && letters[event.key];
  if (!letter) return false;
  event.preventDefault();
  note(`key:${event.key}`, selection.current(ordered())?.id);
  const inList = Boolean(inside(event, "#tasks"));
  letter();
  render();
  if (inList) selection.focusSelected();
  return true;
}

/** @param {KeyboardEvent} event */
export function enterKey(event) {
  if (event.key !== "Enter" || inside(event, "button, a")) return false;
  const open = selection.current(ordered());
  if (open) selection.choose(open.id);
  render();
  return true;
}
