// The gear's menu: the theme for this browser alone; auto leaves it to the system.

import {make} from '../core/dom.js';

const gear = document.getElementById('gear');
const list = document.getElementById('gear-menu');
const root = document.documentElement;

const paint = (choice) => {
  if (choice === 'auto') delete root.dataset.theme;
  else root.dataset.theme = choice;
  list.querySelectorAll('button').forEach((button) => button.setAttribute('aria-pressed', String(button.value === choice)));
};

const shut = (open) => {
  list.hidden = !open;
  gear.setAttribute('aria-expanded', String(open));
};

list.append(...['auto', 'light', 'dark'].map((choice) => Object.assign(make('button', '', choice), {value: choice})));
gear.onclick = () => shut(list.hidden);
list.onclick = ({target}) => {
  if (!target.value) return;
  try {
    if (target.value === 'auto') localStorage.removeItem('theme');
    else localStorage.theme = target.value;
  } catch (blocked) {
    // the choice then lasts until reload
  }
  paint(target.value);
  shut(false);
};
document.addEventListener('pointerdown', (event) => event.target.closest('.menu') || shut(false));
paint(['light', 'dark'].includes(root.dataset.theme) ? root.dataset.theme : 'auto');
