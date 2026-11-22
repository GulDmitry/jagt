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
