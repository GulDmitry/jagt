// The form that adopts an existing review request. Hidden until asked for: it is the rarer of the two ways a
// task starts, and a row of fields nobody uses is a row of fields in the way.

import {api} from '../core/api.js';
import {submits} from './submit.js';

const form = document.getElementById('resume');
const url = document.getElementById('resume-url');
const opener = document.getElementById('resume-task');

const show = (open) => {
  form.hidden = !open;
  opener.setAttribute('aria-pressed', String(open));
};

export function open(link) {
  show(true);
  if (link) url.value = link;
  url.focus();
}

export const close = () => show(false);

export const describe = (hint) => {
  opener.dataset.tip = hint;
  form.querySelector('button[type=submit]').dataset.tip = hint;
};

opener.onclick = () => (form.hidden ? open() : close());

submits(form, {
  waiting: 'reading the review request…',        // a model call unless a CodeHost is configured
  send: () => api('/api/tasks/resume', {
    method: 'POST',
    headers: {'Content-Type': 'application/json'},
    body: JSON.stringify({reviewRequestUrl: url.value}),
  }),
  // A refused request comes back as an ordinary answer, and the url is what would repeat the attempt.
  done: (result) => {
    if (!result.created) return;
    form.reset();
    close();
  },
});
