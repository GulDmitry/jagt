// What was pressed, and two things shown, sent once a burst settles: a name and its task's status, never a task, a
// ticket or a typed word.

import {api} from '../core/api.js';
import * as store from '../core/store.js';

const BATCH = 50;
const QUEUE = 200;
const SETTLE = 2000;

let queue = [];
let due = 0;

const named = (node) => {
  const {action, report, verb, phase, place} = node.dataset;
  if (action) return place ? `action:${action}.${place}` : `action:${action}`;
  if (node.hasAttribute('data-select')) return 'select';
  if (node.hasAttribute('data-back')) return 'back';
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

export const note = (name, taskId) => {
  const control = name && fitted(name);
  if (!control) return;
  queue.push({at: Date.now(), control, status: (taskId && store.taskById(taskId)?.status) || null});
  if (queue.length > QUEUE) queue = queue.slice(-QUEUE);
  if (queue.length >= BATCH) flush();
  else if (!due) due = setTimeout(flush, SETTLE);
};

let required = null;
let jobsFailed = false;

// A sighting is logged when it BEGINS: the first board seen only seeds, since when it turned required is unknown.
export function watch(tasks, failing) {
  if (failing && !jobsFailed) note('shown:jobs-failed');
  jobsFailed = failing;
  if (!tasks.length) return;
  const now = new Set(tasks.filter((task) => task.attention === 'REQUIRED').map((task) => task.id));
  if (required) tasks.filter((task) => now.has(task.id) && !required.has(task.id)).forEach((task) => note('shown:required', task.id));
  required = now;
}

function flush() {
  clearTimeout(due);
  due = 0;
  if (!queue.length) return;
  const presses = queue.splice(0, BATCH);
  if (queue.length) due = setTimeout(flush, SETTLE);
  api('/api/interactions', {method: 'POST', keepalive: true, headers: {'Content-Type': 'application/json'},
    body: JSON.stringify({presses})}).catch(() => {});
}

document.addEventListener('click', (event) => {
  const control = event.target.closest?.('button, a, input[type=checkbox], select');
  if (control) note(named(control), control.closest('[data-task]')?.dataset.task || control.closest('[data-row]')?.dataset.row);
}, true);
document.addEventListener('submit', (event) => note(named(event.target)), true);
window.addEventListener('pagehide', flush);
