// The form that starts a task, and the only place its fields are read.

import {api} from '../core/api.js';
import * as store from '../core/store.js';
import * as projects from './projects.js';
import {submits} from './submit.js';

const form = document.getElementById('launch');
const ref = document.getElementById('ref');
const strategy = document.getElementById('strategy');

export const focusRef = () => ref.focus();

let rendered = null;

export function render() {
  const choices = store.branchStrategies();
  const signature = choices.map((choice) => choice.id).join('\n');
  if (rendered === signature) return;
  rendered = signature;
  const chosenBefore = strategy.value;
  strategy.replaceChildren(...choices.map((choice) => new Option(choice.id, choice.id)));
  // The words are the server's: a page that spelled out what each choice does would be the copy that goes stale.
  strategy.dataset.tip = choices.map((choice) => `${choice.id} — ${choice.hint}`).join(' · ');
  if (choices.some((choice) => choice.id === chosenBefore)) {
    strategy.value = chosenBefore;
  }
}

submits(form, {
  waiting: 'starting…',
  send: () => api('/api/tasks', {
    method: 'POST',
    headers: {'Content-Type': 'application/json'},
    body: JSON.stringify({
      ref: ref.value,
      project: projects.chosen(),
      mode: document.getElementById('plan').checked ? 'plan' : null,
      strategy: strategy.value,
      baseBranch: document.getElementById('base-branch').value,
      notes: document.getElementById('notes').value,
    }),
  }),
  // Most declines arrive as an ordinary answer, and this form holds what the next attempt needs.
  done: (result) => {
    if (!result.created) return;
    form.reset();
    projects.forget();
  },
});
