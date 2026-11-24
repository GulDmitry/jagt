import { api, refusal } from "../core/api.js";
import * as quiet from "./quiet.js";
import { refresh } from "./refresh.js";
import * as tabs from "./tabs.js";
import { toast } from "./toast.js";

/** @param {string} verbId */
export async function runGlobal(verbId) {
  try {
    const result = await api(`/api/commands/${encodeURIComponent(verbId)}`, {
      method: "POST",
    });
    toast(result.message);
  } catch (e) {
    toast(refusal(e), true);
  } finally {
    await refresh();
  }
}

/**
 * @param {import("../core/store.js").Task} task
 * @param {import("../core/store.js").Action} action
 * @param {string} project
 */
export async function post(task, action, project) {
  quiet.clear(task, action);
  try {
    const named = project ? `&project=${encodeURIComponent(project)}` : "";
    const result = await api(
      `/api/tasks/actions/${action.id}?task=${encodeURIComponent(task.id)}${named}`,
      { method: "POST" },
    );
    if (action.id === "code") tabs.choose("code");
    if (result.quiet) quiet.mark(task, action, result.message);
    toast(result.message);
  } catch (e) {
    toast(refusal(e), true);
  }
}
