import { api, cause } from "../core/api.js";
import { t } from "../core/i18n.js";
import * as store from "../core/store.js";
import { boardArrived } from "./refresh.js";
import { toast } from "./toast.js";

const state = { failed: false, read: () => {} };
/** @type {Promise<void>} */
const verbsArrived = new Promise((resolve) => {
  state.read = resolve;
});

/** @type {Promise<unknown>} */
export const stateArrived = Promise.all([verbsArrived, boardArrived]);

export async function refreshVerbs() {
  try {
    store.set({ verbs: await api("/api/commands") });
    state.failed = false;
  } catch (e) {
    store.set({ verbs: [] });
    if (!state.failed) toast(t("refresh.commands", { cause: cause(e) }), true);
    state.failed = true;
  } finally {
    state.read();
  }
}
