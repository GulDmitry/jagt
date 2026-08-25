// The form that starts a task. It owns its fields; what the ticket read costs is why the slot says so while it
// runs.

import {api} from '../core/api.js';
import * as projects from './projects.js';
import {submits} from './submit.js';

const form = document.getElementById('launch');
const ref = document.getElementById('ref');

export const focusRef = () => ref.focus();

submits(form, {
  waiting: 'reading the ticket…',
  send: () => api('/api/tasks', {
    method: 'POST',
    headers: {'Content-Type': 'application/json'},
    body: JSON.stringify({
      ref: ref.value,
      project: projects.chosen(),
      mode: document.getElementById('plan').checked ? 'plan' : null,
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
