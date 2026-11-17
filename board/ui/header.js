import { api } from "../core/api.js";

import { t } from "../core/i18n.js";
import * as store from "../core/store.js";
import * as chips from "./chips.js";
import * as phasebar from "./phasebar.js";

const waitingLabel = /** @type {HTMLElement} */ (
  document.getElementById("waiting")
);
const autoReviewChip = /** @type {HTMLElement} */ (
  document.getElementById("auto-review")
);
const masterChip = /** @type {HTMLElement} */ (
  document.getElementById("master-mode")
);

/**
 * @param {import("../core/store.js").Task[]} tasks
 * @param {number} showing
 */
export function render(tasks, showing) {
  const waiting = tasks.filter((task) => task.attention === "REQUIRED").length;
  waitingLabel.hidden = waiting === 0;
  waitingLabel.textContent = t("header.waiting", { n: waiting });
  // Polling as it should is not news; only the poll being off is said.
  autoReviewChip.hidden = store.autoReview().enabled;
  autoReviewChip.textContent = store.autoReview().summary || "";
  // The Master pressing buttons for the human is said while it does; judging alone shows nothing.
  masterChip.hidden = store.masterMode() !== "act";
  masterChip.textContent = t("header.master");
  masterChip.dataset.tip = t("header.master.tip");
  chips.render();
  phasebar.render(tasks, showing);
}

export async function showVersion() {
  const info = await api("/actuator/info").catch(() => ({}));
  const version = info.build?.version;
  if (version) {
    /** @type {HTMLElement} */ (document.getElementById("title")).dataset.tip =
      `jagt ${version}`;
  }
}
