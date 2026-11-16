import { api, refusal } from "../core/api.js";
import { refresh } from "./refresh.js";
import * as tabs from "./tabs.js";
import { toast } from "./toast.js";

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

export async function post(task, action, project) {
  try {
    const named = project ? `&project=${encodeURIComponent(project)}` : "";
    const result = await api(
      `/api/tasks/actions/${action.id}?task=${encodeURIComponent(task.id)}${named}`,
      { method: "POST" },
    );
    if (action.id === "code") tabs.choose("code");
    toast(result.message);
  } catch (e) {
    toast(refusal(e), true);
  }
}
