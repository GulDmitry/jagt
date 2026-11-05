// The form that starts a task, in its own dialog, and the only place its fields are read.

import {api} from '../core/api.js';
import * as store from '../core/store.js';
import * as projects from './projects.js';
import {submits} from './submit.js';

const dialog = document.getElementById('launch-dialog');
const form = document.getElementById('launch');
const ref = document.getElementById('ref');
const strategy = document.getElementById('strategy');

export const open = () => {
  if (!dialog.open) dialog.showModal();
  ref.focus();
  projects.cards();
};

document.getElementById('close-launch').onclick = () => dialog.close();

// ⌘↵ starts from any field.
form.addEventListener('keydown', (event) => {
  if (event.key === 'Enter' && (event.metaKey || event.ctrlKey)) {
    event.preventDefault();
    form.requestSubmit();
  }
});

let rendered = null;

export function render() {
  const choices = store.branchStrategies();
  const signature = choices.map((choice) => choice.id).join('\n');
  if (rendered === signature) return;
  rendered = signature;
  const chosenBefore = strategy.value;
  strategy.replaceChildren(...choices.map((choice) => new Option(choice.id, choice.id)));
  // The words are the server's.
  strategy.dataset.tip = choices.map((choice) => `${choice.id} — ${choice.hint}`).join(' · ');
  if (choices.some((choice) => choice.id === chosenBefore)) {
    strategy.value = chosenBefore;
  }
}

const plan = document.getElementById('plan');
const notes = document.getElementById('notes');

const problem = () => {
  const ticket = ref.value.trim();
  if (/\s/.test(ticket)) return 'A ticket is a key or a URL, not words';
  if (!ticket && !notes.value.trim()) return 'Give a ticket, or notes saying what to do';
  if (!ticket && !projects.chosen() && store.projects().length !== 1) return 'Choose a project for your notes';
  return projects.chosen() || plan.checked ? null : 'Choose a project, or start with plan first';
};

form.addEventListener('change', () => document.getElementById('launch-state').classList.remove('bad'));

submits(form, {
  problem,
  waiting: 'starting…',
  send: () => api('/api/tasks', {
    method: 'POST',
    headers: {'Content-Type': 'application/json'},
    body: JSON.stringify({
      ref: ref.value,
      project: projects.chosen(),
      mode: plan.checked ? 'plan' : null,
      strategy: strategy.value,
      baseBranch: document.getElementById('base-branch').value,
      notes: notes.value,
    }),
  }),
  done: () => {
    form.reset();
    projects.forget();
    dialog.close();
  },
});
