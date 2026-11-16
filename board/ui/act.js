import { hold, release } from "./inflight.js";
import { intent } from "./intent.js";
import { refresh } from "./refresh.js";
import { refocus, render } from "./render.js";
import { post } from "./verbs.js";

export async function run(taskId, actionId, project = "") {
  const chosen = intent(taskId, actionId);
  if (!chosen) return;
  const { task, action } = chosen;
  const pressed = document.activeElement?.closest?.("#board")
    ? { task: task.id, action: action.id }
    : null;
  hold(task, action);
  render();
  try {
    await post(task, action, project);
  } finally {
    release(task, action);
    await refresh();
    if (pressed) refocus(pressed);
  }
}
