import { run } from "./act.js";
import { note } from "./interactions.js";
import * as selection from "./selection.js";
import { ordered } from "./view.js";

// A verb key acts only on a task whose verbs are on screen; the server still decides whether it may run.
/**
 * @param {KeyboardEvent} event
 * @param {boolean} plain
 * @param {(() => void) | undefined} taken
 */
export function verbKey(event, plain, taken) {
  const open = selection.current(ordered());
  const verb =
    plain && !taken && selection.paneShown()
      ? open?.actions?.find((action) => action.key && action.key === event.key)
      : undefined;
  if (!open || !verb) return false;
  event.preventDefault();
  note(`key:${event.key}`, open.id);
  run(open.id, verb.id);
  return true;
}
