import { api, refusal } from "../core/api.js";
import { t } from "../core/i18n.js";
import { labelOf, paint, unread } from "./settings-view.js";
import { toast } from "./toast.js";
import { runGlobal } from "./verbs.js";

/** @typedef {import("./settings-view.js").Setting} Setting */

const dialog = /** @type {HTMLDialogElement} */ (
  document.getElementById("settings-dialog")
);
const body = /** @type {HTMLElement} */ (
  document.getElementById("settings-body")
);
const restartBar = /** @type {HTMLElement} */ (
  document.getElementById("settings-restart")
);
/** @type {{ waiting: Set<string> }} */
const state = { waiting: new Set() };

const paintRestart = () => {
  restartBar.classList.toggle("waiting", state.waiting.size > 0);
  /** @type {HTMLElement} */ (
    document.getElementById("settings-restart-text")
  ).textContent = t("settings.restart", {
    keys: [...state.waiting].join(", "),
  });
};

/**
 * @param {Setting} setting
 * @param {unknown} value
 */
async function change(setting, value) {
  try {
    const result = await api("/api/settings", {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        key: setting.key,
        value,
      }),
    });
    toast(result.message);
    body
      .querySelector(`[data-key="${setting.key}"] .hint`)
      ?.replaceChildren(t("settings.set"));
    if (result.restart) {
      state.waiting.add(labelOf(setting.key));
      paintRestart();
    }
  } catch (e) {
    toast(refusal(e), true);
    await load();
  }
}

async function load() {
  try {
    paint(body, await api("/api/settings"), change);
  } catch (e) {
    unread(body, refusal(e));
  }
}

document.addEventListener("settings-open", () => {
  if (!dialog.open) dialog.showModal();
  load();
});
document
  .getElementById("project-dialog")
  ?.addEventListener("close", () => dialog.open && load());
/** @type {HTMLElement} */ (document.getElementById("close-settings")).onclick =
  () => dialog.close();
/** @type {HTMLElement} */ (
  document.getElementById("settings-restart-go")
).onclick = () => runGlobal("restart");
