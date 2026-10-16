// The form that adds a project: the path is read as it is typed, and what the reading suggests fills the fields
// a human has not touched. Writing is the server's; this page only asks.

import {render} from 'preact';
import {api, refusal} from '../core/api.js';
import {html} from '../components/html.js';
import {ProjectChecks} from '../components/ProjectChecks.js';
import {sending} from './submit.js';

const dialog = document.getElementById('project-dialog');
const form = document.getElementById('project-form');
const path = document.getElementById('project-path');
const checks = document.getElementById('project-checks');
const suggested = {key: 'project-key', baseBranch: 'project-base', deployBranch: 'project-deploy'};
const SETTLE = 400;
let due = 0;
let asked = 0;

export const open = () => {
  if (!dialog.open) dialog.showModal();
  path.focus();
};

// A field the human typed into is theirs; only an untouched one takes what the reading suggests.
form.addEventListener('input', (event) => {
  if (event.target !== path) event.target.dataset.typed = 'true';
});

const fill = (id, value) => {
  const field = document.getElementById(id);
  if (!field.dataset.typed) field.value = value || '';
};

async function read() {
  const mine = ++asked;
  if (!path.value.trim()) {
    render(null, checks);
    return;
  }
  try {
    const reading = await api(`/api/projects/check?path=${encodeURIComponent(path.value.trim())}`);
    if (mine !== asked) return;
    render(html`<${ProjectChecks} lines=${reading.lines}/>`, checks);
    Object.entries(suggested).forEach(([key, id]) => fill(id, reading[key]));
    fill('project-verify', (reading.verifyCommand || []).join(' '));
  } catch (e) {
    if (mine === asked) render(html`<${ProjectChecks} lines=${[{ok: false, text: refusal(e)}]}/>`, checks);
  }
}

path.addEventListener('input', () => {
  clearTimeout(due);
  due = setTimeout(read, SETTLE);
});

document.getElementById('close-project').onclick = () => dialog.close();

form.onsubmit = (event) => {
  event.preventDefault();
  const verify = document.getElementById('project-verify').value.trim();
  return sending(form, {
    waiting: 'adding…',
    send: () => api('/api/projects', {
      method: 'POST',
      headers: {'Content-Type': 'application/json'},
      body: JSON.stringify({
        key: document.getElementById('project-key').value.trim(),
        path: path.value.trim(),
        baseBranch: document.getElementById('project-base').value.trim(),
        deployBranch: document.getElementById('project-deploy').value.trim() || null,
        verifyCommand: verify ? verify.split(/\s+/) : null,
        about: document.getElementById('project-about').value.trim() || null,
      }),
    }),
    done: () => {
      form.reset();
      form.querySelectorAll('[data-typed]').forEach((field) => delete field.dataset.typed);
      render(null, checks);
      dialog.close();
    },
  });
};
