// The Reports menu: one button opens the list, and a pick, a press elsewhere or Escape closes it.

import * as store from '../core/store.js';

const toggle = document.getElementById('reports-toggle');
const list = document.getElementById('reports');

export const close = () => {
  const wasOpen = !list.hidden;
  list.hidden = true;
  toggle.setAttribute('aria-expanded', 'false');
  return wasOpen;
};

const open = () => {
  list.hidden = false;
  toggle.setAttribute('aria-expanded', 'true');
  list.querySelector('button')?.focus();
};

let picked = () => {};

export const onPick = (open) => { picked = open; };

// A report about ONE task gets no button: the card that has something to show is where it is pressed, and a menu
// entry would answer for all of them.
export const render = () => list.replaceChildren(
  ...store.verbs().filter((verb) => verb.report && !verb.aboutOneTask).map((verb) => {
    const button = document.createElement('button');
    button.setAttribute('role', 'menuitem');
    button.id = `show-${verb.id}`;
    button.dataset.verb = verb.id;
    button.append(verb.id.charAt(0).toUpperCase() + verb.id.slice(1),
      Object.assign(document.createElement('span'), {className: 'muted', textContent: verb.hint}));
    return button;
  }));

toggle.onclick = () => (list.hidden ? open() : close());
list.addEventListener('click', (event) => {
  const button = event.target.closest('button');
  if (!button) return;
  close();
  picked(button.dataset.verb);
});
document.addEventListener('pointerdown', (event) => {
  if (!list.hidden && !event.target.closest('.menu')) close();
});
