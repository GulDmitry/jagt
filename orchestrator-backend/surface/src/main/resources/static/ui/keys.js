// Every binding in one place, so a new panel adds no second answer to Escape.

import {run} from './act.js';
import * as filters from './filters.js';
import {note} from './interactions.js';
import * as launch from './launch.js';
import * as menu from './menu.js';
import * as palette from './palette.js';
import {ordered, render} from './render.js';
import * as selection from './selection.js';

const typingInto = (target) => target instanceof HTMLInputElement || target instanceof HTMLTextAreaElement
  || target instanceof HTMLSelectElement;

// What the keys do, said once under the list; the bindings below are what it describes.
export const said = 'j k walk · ↵ open · n next yours · f focus · t new task · / filter · ⌘K command';

// A letter moves the selection or opens a form; any other letter is the key of a verb the open task offers, and the
// server still decides whether it may run.
const letters = {
  t: () => launch.open(),
  j: () => selection.step(ordered(), 1),
  k: () => selection.step(ordered(), -1),
  n: () => selection.nextRequired(ordered()),
};

document.addEventListener('keydown', (event) => {
  // Behind a modal the board is inert: a shortcut would change it unseen.
  if (document.querySelector('dialog[open]')) return;
  if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 'k') {
    event.preventDefault();
    palette.toggle();
    return;
  }
  if (typingInto(event.target) && event.key !== 'Escape') return;
  if (event.key === '/') {
    event.preventDefault();
    filters.box.focus();
    return;
  }
  const plain = !event.metaKey && !event.ctrlKey && !event.altKey;
  const open = selection.current(ordered());
  // A verb key acts only on a task whose verbs are on screen.
  const verb = plain && !letters[event.key] && selection.paneShown()
    && open?.actions?.find((action) => action.key && action.key === event.key);
  if (verb) {
    event.preventDefault();
    note(`key:${event.key}`, open.id);
    run(open.id, verb.id);
    return;
  }
  const letter = plain && letters[event.key];
  if (letter) {
    event.preventDefault();
    note(`key:${event.key}`, selection.current(ordered())?.id);
    const inList = Boolean(event.target.closest?.('#tasks'));
    letter();
    render();
    if (inList) selection.focusSelected();
    return;
  }
  if (event.key === 'Enter' && !event.target.closest?.('button, a')) {
    const open = selection.current(ordered());
    if (open) selection.choose(open.id);
    render();
    return;
  }
  // A <dialog> closes on Escape by itself; the inline forms do not. One press closes the innermost thing open.
  if (event.key !== 'Escape') return;
  const closed = palette.close() || menu.close() || (event.target === filters.box && filters.clearText());
  if (!closed) selection.back();
  render();
});
