// The projects a new task can run in, one box each, filled from the same snapshot the board renders.

import {api, refusal} from '../core/api.js';
import * as store from '../core/store.js';

const group = document.getElementById('project');
let rendered = null;

// The order they were ticked in is not kept, so the CONFIGURED order decides which repository the agent's
// session runs in. None ticked sends nothing: naming a project skips the ticket read.
export const chosen = () => {
  const ticked = new Set([...group.querySelectorAll('input:checked')].map((box) => box.value));
  return store.projects().filter((project) => ticked.has(project)).join(',');
};

export const forget = () => {
  group.querySelectorAll('input').forEach((box) => { box.checked = false; });
};

const said = (card) => Object.entries(card).filter(([, value]) => value?.length)
  .map(([name, value]) => `${name}: ${[value].flat().join(', ')}`).join('\n');

// A remote's address is read from git, so the cards are read again whenever the form opens.
export const cards = () => Promise.all([...group.querySelectorAll('label')].map(async (label) => {
  const key = label.firstElementChild.value;
  label.dataset.tip = await api(`/api/projects/${encodeURIComponent(key)}/card`).then(said, (e) => `${key}: ${refusal(e)}`);
}));

export function render() {
  const configured = store.projects();
  const signature = configured.join('\n');
  if (rendered === signature) {
    return;                       // a rebuild under a human ticking boxes would drop the ticks
  }
  rendered = signature;
  const ticked = new Set(chosen().split(',').filter(Boolean));
  const byName = [...configured].sort((a, b) => a.localeCompare(b, 'en'));
  group.replaceChildren(...(byName.length ? byName.map((project) => {
    const label = document.createElement('label');
    label.className = 'checkbox';
    const box = Object.assign(document.createElement('input'), {type: 'checkbox', value: project, name: 'project'});
    box.checked = ticked.has(project);
    label.append(box, ` ${project}`);
    return label;
  }) : [Object.assign(document.createElement('span'), {className: 'muted', textContent: 'no projects in jagt.yml'})]));
}
