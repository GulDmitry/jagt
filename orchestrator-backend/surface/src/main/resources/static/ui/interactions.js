// What was pressed, sent once a burst settles: a control's name and its task's status, never a task, a ticket or a
// typed word.

import * as store from '../core/store.js';

const BATCH = 50;
const QUEUE = 200;
const SETTLE = 2000;

let queue = [];
let due = 0;

const named = (node) => {
  const {action, report, verb, phase} = node.dataset;
  if (action) return `action:${action}`;
  if (report) return `report:${report}`;
  if (verb) return `verb:${verb}`;
  if (phase) return 'phase';
  if (node.hasAttribute('data-flag')) return 'flag';
  if (node.hasAttribute('data-order')) return 'order';
  if (node.hasAttribute('data-clear')) return 'clear-filters';
  if (node.id) return `id:${node.id}`;
  return node.classList.length ? `class:${node.classList[0]}` : null;
};

// The server refuses a whole batch over one malformed name, so a name is made to fit before it is queued.
const fitted = (name) => name.toLowerCase().replace(/[^a-z0-9:#._-]/g, '-').replace(/^[^a-z0-9]+/, '').slice(0, 48);

const note = (name, taskId) => {
  const control = name && fitted(name);
  if (!control) return;
  queue.push({at: Date.now(), control, status: (taskId && store.taskById(taskId)?.status) || null});
  if (queue.length > QUEUE) queue = queue.slice(-QUEUE);
  if (queue.length >= BATCH) flush();
  else if (!due) due = setTimeout(flush, SETTLE);
};

function flush() {
  clearTimeout(due);
  due = 0;
  if (!queue.length) return;
  const presses = queue.splice(0, BATCH);
  if (queue.length) due = setTimeout(flush, SETTLE);
  fetch('/api/interactions', {method: 'POST', keepalive: true, headers: {'Content-Type': 'application/json'},
    body: JSON.stringify({presses})}).catch(() => {});
}

document.addEventListener('click', (event) => {
  const control = event.target.closest?.('button, a, input[type=checkbox], select');
  if (control) note(named(control), control.closest('[data-task]')?.dataset.task);
}, true);
document.addEventListener('submit', (event) => note(named(event.target)), true);
window.addEventListener('pagehide', flush);
