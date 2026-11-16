import { api } from "../core/api.js";
import { t } from "../core/i18n.js";
import * as store from "../core/store.js";
import { render } from "./render.js";
import { toast } from "./toast.js";

const state = {
  asked: 0,
  painted: 0,
  boardRead: undefined,
  verbsFailed: false,
  verbsRead: undefined,
};
export const boardArrived = new Promise((resolve) => {
  state.boardRead = resolve;
});

export async function refresh() {
  const mine = ++state.asked;
  try {
    const data = await api("/api/tasks");
    // Reads overlap, and one answered late describes the board before a later one.
    if (mine < state.painted) return;
    state.painted = mine;
    store.set({
      tasks: data.tasks,
      projects: data.projects || [],
      branchStrategies: data.branchStrategies || [],
      phases: data.phases || [],
      autoReview: { summary: data.autoReview, enabled: data.autoReviewEnabled },
      jobs: data.jobs,
      actStop: data.actStop,
      reach: data.reach || [],
      masterMode: data.masterMode,
      offers: data.offers || {},
    });
    render();
  } catch (e) {
    toast(t("refresh.backend", { cause: e.message }), true);
  } finally {
    state.boardRead();
  }
}

// Settles once the first read of the verbs does, failed or not: a line parsed before it would go to the model.
export const verbsArrived = new Promise((resolve) => {
  state.verbsRead = resolve;
});

// Fetched, not hardcoded: the palette completes and validates against the server's own verb list. Without it
// the palette degrades rather than breaks, said once per failing streak rather than on every reconnect.
export async function refreshVerbs() {
  try {
    store.set({ verbs: await api("/api/commands") });
    state.verbsFailed = false;
  } catch (e) {
    store.set({ verbs: [] });
    if (!state.verbsFailed)
      toast(t("refresh.commands", { cause: e.message }), true);
    state.verbsFailed = true;
  } finally {
    state.verbsRead();
  }
}
