// The gear's menu: the theme and the language for this browser alone; auto leaves the theme to the system.

import {make} from '../core/dom.js';
import {choose, current, languages, t} from '../core/i18n.js';

const gear = document.getElementById('gear');
const list = document.getElementById('gear-menu');
const root = document.documentElement;

const paint = (choice) => {
  if (choice === 'auto') delete root.dataset.theme;
  else root.dataset.theme = choice;
  list.querySelectorAll('button[value]').forEach((button) => button.setAttribute('aria-pressed', String(button.value === choice)));
};

const shut = (open) => {
  list.hidden = !open;
  gear.setAttribute('aria-expanded', String(open));
};

list.append(...['auto', 'light', 'dark'].map((choice) => Object.assign(make('button', '', t(`theme.${choice}`)), {value: choice})));
const offer = () => languages().then((offered) => list.append(...offered.map(({code, name}) => {
  const button = make('button', '', name);
  button.dataset.lang = code;
  button.setAttribute('aria-pressed', String(code === current()));
  return button;
})));
gear.addEventListener('click', offer, {once: true});
gear.onclick = () => shut(list.hidden);
list.onclick = ({target}) => {
  if (target.dataset.lang) {
    choose(target.dataset.lang);
    window.location.reload();
    return;
  }
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
