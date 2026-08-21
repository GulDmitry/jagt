// One action on one task, whether a button was pressed or a line was typed: the same gate, the same lock, the
// same refusal. The page decides nothing about WHETHER an action is legal — the server has the fresh state and
// refuses with a sentence, which is what the toast shows.

import {api, refusal} from '../core/api.js';
import * as store from '../core/store.js';
import {confirmation} from './confirm.js';
import {openTerminal} from './dialogs.js';
import {blocked, hold, release, waitingFor} from './inflight.js';
import {refresh} from './refresh.js';
import {render} from './render.js';
import {toast} from './toast.js';

// A verb the card does not offer is still SENT: this page may be describing a task that has moved on, and the
// server's refusal says so in words. It counts as a write, the safe side — every read-only action is offered
// from every status, so the fallback is reached by writes alone.
const actionOf = (task, actionId) => (task.actions || []).find((each) => each.id === actionId)
  || {id: actionId, label: actionId, hint: '', readOnly: false};

export async function run(taskId, actionId) {
  const task = store.taskFor(taskId);
  if (!task) {
    return;                       // the card went away between the click and this line
  }
  const action = actionOf(task, actionId);
  if (blocked(task, action)) {
    toast(`${task.id} is already running ${waitingFor(task, action)} — wait for that to finish`, true);
    return;
  }
  const question = confirmation(task, action);
  if (question && !confirm(question)) {
    return;
  }
  hold(task, action);
  render();
  try {
    const result = await api(`/api/tasks/${encodeURIComponent(task.id)}/actions/${action.id}`, {method: 'POST'});
    toast(result.message);
    if (action.id === 'focus') await openTerminal(task);
  } catch (e) {
    toast(refusal(e), true);
  } finally {
    release(task, action);
    await refresh();
  }
}
