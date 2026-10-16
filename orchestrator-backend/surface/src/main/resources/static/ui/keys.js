// Every binding in one place, so a new panel adds no second answer to Escape.

import {run} from './act.js';
import * as filters from './filters.js';
import {note} from './interactions.js';
import * as palette from './palette.js';
import {ordered, render} from './render.js';
import * as selection from './selection.js';

const typingInto = (target) => target instanceof HTMLInputElement || target instanceof HTMLTextAreaElement
  || target instanceof HTMLSelectElement;

// A letter moves the selection or runs a verb the open task offers; the server still decides whether it may.
const letters = {
  j: () => selection.step(ordered(), 1),
  k: () => selection.step(ordered(), -1),
  n: () => selection.nextRequired(ordered()),
  f: () => {
    const open = selection.current(ordered());
    if (open?.actions?.some((action) => action.id === 'focus')) run(open.id, 'focus');
  },
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
  const letter = !event.metaKey && !event.ctrlKey && !event.altKey && letters[event.key];
  if (letter) {
    event.preventDefault();
    note(`key:${event.key}`, selection.current(ordered())?.id);
    letter();
    render();
    return;
  }
  if (event.key === 'Enter' && !event.target.closest?.('button, a')) {
    const open = selection.current(ordered());
    if (open) selection.choose(open.id);
    render();
    return;
  }
  // A <dialog> closes on Escape by itself; the inline forms do not.
  if (event.key !== 'Escape') return;
  if (event.target === filters.box) {
    filters.box.value = '';
  }
  selection.back();
  palette.close();
  render();
});
