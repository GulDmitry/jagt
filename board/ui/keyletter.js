import { note } from "./interactions.js";
import * as launch from "./launch.js";
import { render } from "./render.js";
import * as selection from "./selection.js";
import { ordered } from "./view.js";

// A letter moves the selection or opens a form.
export const letters = {
  t: () => launch.open(),
  j: () => selection.step(ordered(), 1),
  k: () => selection.step(ordered(), -1),
  n: () => selection.nextRequired(ordered()),
};

export function letterKey(event, plain) {
  const letter = plain && letters[event.key];
  if (!letter) return false;
  event.preventDefault();
  note(`key:${event.key}`, selection.current(ordered())?.id);
  const inList = Boolean(event.target.closest?.("#tasks"));
  letter();
  render();
  if (inList) selection.focusSelected();
  return true;
}

export function enterKey(event) {
  if (event.key !== "Enter" || event.target.closest?.("button, a"))
    return false;
  const open = selection.current(ordered());
  if (open) selection.choose(open.id);
  render();
  return true;
}
