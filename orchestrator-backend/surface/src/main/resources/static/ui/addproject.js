// Adds or edits a project: the path is read as typed and fills the fields untouched.

import {render} from 'preact';
import {api, refusal} from '../core/api.js';
import {t} from '../core/i18n.js';
import {html} from '../components/html.js';
import {ProjectChecks} from '../components/ProjectChecks.js';
import {sending} from './submit.js';

const $ = (id) => document.getElementById(id);
const dialog = $('project-dialog');
const form = $('project-form');
const path = $('project-path');
const checks = $('project-checks');
const suggested = {key: 'project-key', baseBranch: 'project-base', deployBranch: 'project-deploy'};
const folders = $('project-folders');
const branches = $('project-branches');
const SETTLE = 400;
const LISTING = 150;
let due = 0, listing = 0, asked = 0, listed = 0;

let editing = null;

const reset = (key = null) => {
  form.reset();
  form.querySelectorAll('[data-typed]').forEach((field) => delete field.dataset.typed);
  render(null, checks);
  editing = key;
  $('project-title').textContent = key ? t('project.edit', {key}) : t('project.add');
  form.querySelector('button[type=submit]').textContent = t(key ? 'project.submit.save' : 'project.add-submit');
  $('project-key').readOnly = !!key;
};

export const open = () => {
  if (!dialog.open) dialog.showModal();
  path.focus();
};

async function edit(key) {
  const entry = await api(`/api/projects/${encodeURIComponent(key)}`);
  reset(key);
  Object.entries({...entry, verify: (entry.verifyCommand || []).join(' '), labels: (entry.labels || []).join(', '),
    humanPaths: (entry.humanPaths || []).join(', ')}).forEach(([name, value]) => {
    if (form.elements[name]) Object.assign(form.elements[name], {value: value || ''}).dataset.typed = 'true';
  });
  open();
  read();
}

export async function argued(argument) {
  const named = /^edit\s+(\S+)$/.exec(argument.trim());
  if (named) await edit(named[1]);
  return !!named;
}

// A field typed into is the human's; the reading fills only the rest.
form.addEventListener('input', (event) => {
  if (event.target !== path) event.target.dataset.typed = 'true';
});

const fill = (id, value) => {
  const field = $(id);
  if (!field.dataset.typed) field.value = value || '';
};

const option = (value, label) => Object.assign(document.createElement('option'), {value, label});

// Each folder under the last typed one completes the path.
async function offerFolders() {
  const mine = ++listed;
  const typed = path.value;
  const slash = typed.lastIndexOf('/');
  if (slash < 0) {
    folders.replaceChildren();
    return;
  }
  try {
    const found = await api(`/api/projects/dirs?under=${encodeURIComponent(typed)}`);
    if (mine === listed) {
      folders.replaceChildren(...found.map((each) =>
        option(`${typed.slice(0, slash + 1)}${each.name}/`, each.repository ? t('project.repository') : '')));
    }
  } catch (e) {
    if (mine === listed) folders.replaceChildren();
  }
}

async function read() {
  const mine = ++asked;
  if (!path.value.trim()) {
    render(null, checks);
    return;
  }
  try {
    const reading = await api(`/api/projects/check?path=${encodeURIComponent(path.value.trim())}${editing ? `&own=${encodeURIComponent(editing)}` : ''}`);
    if (mine !== asked) return;
    render(html`<${ProjectChecks} lines=${reading.lines}/>`, checks);
    Object.entries(suggested).forEach(([key, id]) => fill(id, reading[key]));
    fill('project-verify', (reading.verifyCommand || []).join(' '));
    branches.replaceChildren(...(reading.deployBranches || []).map((name) => option(name, '')));
  } catch (e) {
    if (mine === asked) render(html`<${ProjectChecks} lines=${[{ok: false, text: refusal(e)}]}/>`, checks);
  }
}

path.addEventListener('input', () => {
  clearTimeout(listing);
  listing = setTimeout(offerFolders, LISTING);
  clearTimeout(due);
  due = setTimeout(read, SETTLE);
});

$('close-project').onclick = () => dialog.close();
dialog.addEventListener('close', () => reset());

const said = (name) => form.elements[name].value.trim();
const list = (name) => {
  const each = said(name).split(',').map((one) => one.trim()).filter(Boolean);
  return each.length ? each : null;
};

form.onsubmit = (event) => {
  event.preventDefault();
  return sending(form, {
    waiting: t(editing ? 'project.saving' : 'project.adding'),
    send: () => api(editing ? `/api/projects/${encodeURIComponent(editing)}` : '/api/projects', {
      method: editing ? 'PUT' : 'POST',
      headers: {'Content-Type': 'application/json'},
      body: JSON.stringify({
        key: said('key'),
        path: said('path'),
        baseBranch: said('baseBranch'),
        deployBranch: said('deployBranch') || null,
        verifyCommand: said('verify') ? said('verify').split(/\s+/) : null,
        about: said('about') || null,
        labels: list('labels'),
        humanPaths: list('humanPaths'),
      }),
    }),
    done: () => dialog.close(),
  });
};
