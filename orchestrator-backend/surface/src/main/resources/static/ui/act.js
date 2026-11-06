// One action on one task, pressed or typed: the page decides nothing about whether it is legal.

import {api, refusal} from '../core/api.js';
import {t} from '../core/i18n.js';
import * as store from '../core/store.js';
import {blocked, hold, release, waitingFor} from './inflight.js';
import {refresh} from './refresh.js';
import {refocus, render} from './render.js';
import {toast} from './toast.js';

// A verb the card does not offer is still SENT: this page may be describing a task that has moved on, and it
// counts as a write, the safe side.
const actionOf = (task, actionId) => (task.actions || []).find((each) => each.id === actionId)
  || {id: actionId, label: actionId, hint: '', readOnly: false};

export async function runGlobal(verbId) {
  try {
    const result = await api(`/api/commands/${encodeURIComponent(verbId)}`, {method: 'POST'});
    toast(result.message);
  } catch (e) {
    toast(refusal(e), true);
  } finally {
    await refresh();
  }
}

export async function run(taskId, actionId, project = '') {
  const task = store.taskById(taskId);
  if (!task) {
    toast(t('act.gone', {task: taskId}), true);
    return;
  }
  const action = actionOf(task, actionId);
  if (blocked(task, action)) {
    toast(t('act.running', {task: task.id, what: waitingFor(task, action)}), true);
    return;
  }
  const question = (task.confirmations || {})[action.id];
  if (question && !confirm(question)) {
    return;
  }
  const pressed = document.activeElement?.closest?.('#board') ? {task: task.id, action: action.id} : null;
  hold(task, action);
  render();
  try {
    const named = project ? `&project=${encodeURIComponent(project)}` : '';
    const result = await api(`/api/tasks/actions/${action.id}?task=${encodeURIComponent(task.id)}${named}`,
      {method: 'POST'});
    toast(result.message);
  } catch (e) {
    toast(refusal(e), true);
  } finally {
    release(task, action);
    await refresh();
    if (pressed) refocus(pressed);
  }
}
