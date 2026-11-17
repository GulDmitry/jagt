import { t } from "../core/i18n.js";
import * as store from "../core/store.js";
import { blocked, waitingFor } from "./inflight.js";
import { toast } from "./toast.js";

// A verb the card does not offer is still SENT: this page may be describing a task that has moved on, and it
// counts as a write, the safe side.
/**
 * @param {import("../core/store.js").Task} task
 * @param {string} actionId
 */
const actionOf = (task, actionId) =>
  (task.actions || []).find((each) => each.id === actionId) || {
    id: actionId,
    label: actionId,
    hint: "",
    readOnly: false,
  };

/**
 * The task and verb to send, or null once the board has said why not.
 * @param {string} taskId
 * @param {string} actionId
 */
export function intent(taskId, actionId) {
  const task = store.taskById(taskId);
  if (!task) {
    toast(t("act.gone", { task: taskId }), true);
    return null;
  }
  const action = actionOf(task, actionId);
  if (blocked(task, action)) {
    toast(
      t("act.running", { task: task.id, what: waitingFor(task, action) }),
      true,
    );
    return null;
  }
  const question = (task.confirmations || {})[action.id];
  if (question && !confirm(question)) {
    return null;
  }
  return { task, action };
}
