// One action on one task, pressed or typed: the page decides nothing about whether it is legal.

import {api, refusal} from '../core/api.js';
import * as store from '../core/store.js';
import {confirmation} from './confirm.js';
import {blocked, hold, release, waitingFor} from './inflight.js';
import {refresh} from './refresh.js';
import {render} from './render.js';
import {toast} from './toast.js';

// A verb the card does not offer is still SENT: this page may be describing a task that has moved on, and it
// counts as a write, the safe side.
const actionOf = (task, actionId) => (task.actions || []).find((each) => each.id === actionId)
  || {id: actionId, label: actionId, hint: '', readOnly: false};

export async function run(taskId, actionId) {
  const task = store.taskById(taskId);
  if (!task) {
    toast(`no task ${taskId} on the board any more`, true);
    return;
  }
  const action = actionOf(task, actionId);
  if (blocked(task, action)) {
    toast(`${task.id} is already running ${waitingFor(task, action)}`, true);
    return;
  }
  const question = confirmation(task, action);
  if (question && !confirm(question)) {
    return;
  }
  hold(task, action);
  render();
  try {
    const result = await api(`/api/tasks/actions/${action.id}?task=${encodeURIComponent(task.id)}`, {method: 'POST'});
    toast(result.message);
  } catch (e) {
    toast(refusal(e), true);
  } finally {
    release(task, action);
    await refresh();
  }
}
