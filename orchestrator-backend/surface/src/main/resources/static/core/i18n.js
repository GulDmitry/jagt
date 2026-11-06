// The board's own words: the chosen language over English, which is imported so the page's load waits for it.

import english from '../api/languages/en' with {type: 'json'};

const get = (code) => fetch(`api/languages/${code}`).then((response) => (response.ok ? response.json() : {}), () => ({}));

let code = 'en';
try { code = localStorage.lang || code; } catch (blocked) { /* English */ }
const chosen = code === 'en' ? english : await get(code);

const platform = {mod: /Mac|iPhone|iPad/.test(navigator.userAgent) ? '⌘' : 'Ctrl+'};

export const t = (key, values = {}) => (chosen[key] ?? english[key] ?? key)
  .replace(/\{(\w+)\}/g, (hole, name) => values[name] ?? platform[name] ?? hole);

export const current = () => code;

export const choose = (next) => {
  try { localStorage.lang = next; } catch (blocked) { /* until reload */ }
};

export const languages = () => fetch('api/languages').then((response) => response.json())
  .then((codes) => Promise.all(codes.map((one) => get(one).then((words) => ({code: one, name: words['language.name'] || one})))));

// `data-t` is an element's text, `data-t-X` its attribute X (`tip` is data-tip).
document.documentElement.lang = code;
document.querySelectorAll('*').forEach((node) => [...node.attributes].forEach(({name, value}) => {
  if (name === 'data-t') node.textContent = t(value);
  else if (name.startsWith('data-t-')) node.setAttribute(name === 'data-t-tip' ? 'data-tip' : name.slice(7), t(value));
}));
