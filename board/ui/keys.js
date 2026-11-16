import * as filters from "./filters.js";
import { escapeKey } from "./keyclose.js";
import { enterKey, letterKey, letters } from "./keyletter.js";
import { verbKey } from "./keyverb.js";
import * as palette from "./palette.js";

const typingInto = (target) =>
  target instanceof HTMLInputElement ||
  target instanceof HTMLTextAreaElement ||
  target instanceof HTMLSelectElement;

const toggling = (event) =>
  (event.metaKey || event.ctrlKey) && event.key.toLowerCase() === "k";

// The keys the board answers whatever is focused, and the ones a field keeps for itself.
function claimed(event) {
  if (toggling(event)) {
    event.preventDefault();
    palette.toggle();
    return true;
  }
  if (typingInto(event.target) && event.key !== "Escape") return true;
  if (event.key !== "/") return false;
  event.preventDefault();
  filters.box.focus();
  return true;
}

document.addEventListener("keydown", (event) => {
  // Behind a modal the board is inert: a shortcut would change it unseen.
  if (document.querySelector("dialog[open]") || claimed(event)) return;
  const plain = !event.metaKey && !event.ctrlKey && !event.altKey;
  verbKey(event, plain, letters[event.key]) ||
    letterKey(event, plain) ||
    enterKey(event) ||
    escapeKey(event);
});
