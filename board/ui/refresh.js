import { api, cause } from "../core/api.js";
import { t } from "../core/i18n.js";
import * as store from "../core/store.js";
import { render } from "./render.js";
import { toast } from "./toast.js";

/** @type {{ asked: number, painted: number, boardRead: () => void, verbsFailed: boolean, verbsRead: () => void }} */
const state = {
  asked: 0,
  painted: 0,
  boardRead: () => {},
  verbsFailed: false,
  verbsRead: () => {},
};
/** @type {Promise<void>} */
export const boardArrived = new Promise((resolve) => {
  state.boardRead = resolve;
});

export async function refresh() {
  const mine = ++state.asked;
  try {
    const data = await api("/api/tasks");
    if (mine < state.painted) return;
    state.painted = mine;
    store.set({
      tasks: data.tasks,
      projects: data.projects || [],
      branchStrategies: data.branchStrategies || [],
      phases: data.phases || [],
      autoReview: { summary: data.autoReview, enabled: data.autoReviewEnabled },
      jobs: data.jobs,
      chips: data.chips || [],
      roleLogs: data.roleLogs || [],
      reach: data.reach || [],
      offers: data.offers || {},
    });
    render();
  } catch (e) {
    toast(t("refresh.backend", { cause: cause(e) }), true);
  } finally {
    state.boardRead();
  }
}

/** @type {Promise<void>} */
export const verbsArrived = new Promise((resolve) => {
  state.verbsRead = resolve;
});

export async function refreshVerbs() {
  try {
    store.set({ verbs: await api("/api/commands") });
    state.verbsFailed = false;
  } catch (e) {
    store.set({ verbs: [] });
    if (!state.verbsFailed)
      toast(t("refresh.commands", { cause: cause(e) }), true);
    state.verbsFailed = true;
  } finally {
    state.verbsRead();
  }
}
