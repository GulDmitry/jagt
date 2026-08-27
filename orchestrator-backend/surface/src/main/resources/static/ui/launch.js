// The form that starts a task. It owns its fields; what the ticket read costs is why the slot says so while it
// runs.

import {api} from '../core/api.js';
import * as store from '../core/store.js';
import * as projects from './projects.js';
import {submits} from './submit.js';

const form = document.getElementById('launch');
const ref = document.getElementById('ref');
const strategy = document.getElementById('strategy');

export const focusRef = () => ref.focus();

// The words are the server's: what each choice does is one sentence in one place, and a page that spelled them
// out again would be the copy that goes stale.
let rendered = null;

export function render() {
  const choices = store.branchStrategies();
  const signature = choices.map((choice) => choice.id).join('\n');
  if (rendered === signature) {
    return;                       // a rebuild collapses the list under a human who has it open
  }
  rendered = signature;
  const chosenBefore = strategy.value;
  strategy.replaceChildren(...choices.map((choice) => new Option(choice.id, choice.id)));
  strategy.dataset.tip = choices.map((choice) => `${choice.id} — ${choice.hint}`).join(' · ');
  if (choices.some((choice) => choice.id === chosenBefore)) {
    strategy.value = chosenBefore;
  }
}

submits(form, {
  waiting: 'reading the ticket…',
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
  // Most of the ways a launch declines arrive as an ordinary answer, not as an error — and this form is
  // holding the project, the branch and the instructions the next attempt needs.
  done: (result) => {
    if (!result.created) return;
    form.reset();
    projects.forget();
  },
});
