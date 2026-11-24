import { make } from "../core/dom.js";
import { countdown } from "../core/format.js";
import { t } from "../core/i18n.js";
import * as store from "../core/store.js";
import { full } from "../core/time.js";

const jobsChip = /** @type {HTMLButtonElement} */ (
  document.getElementById("jobs-pulse")
);
const reachChip = /** @type {HTMLButtonElement} */ (
  document.getElementById("reach-lost")
);
const roleChips = /** @type {HTMLElement} */ (
  document.getElementById("role-chips")
);

/** @param {number} due */
const left = (due) => (due > 0 ? countdown(due) : t("due"));

function renderJobs() {
  const jobs = store.jobs();
  if (!jobs || !jobs.failing) {
    jobsChip.hidden = true;
    return;
  }
  jobsChip.hidden = false;
  // The stamp is the last change's, so it goes into the past: past means DUE.
  const due = jobs.nextRunAt ? jobs.nextRunAt - Date.now() : null;
  jobsChip.textContent = t("jobs-failed", {
    failing: jobs.failing,
    count: jobs.count,
  });
  jobsChip.dataset.tip = t("header.jobs.tip", {
    next: due === null ? "-" : left(due),
  });
}

function renderReach() {
  const lost = store.reach();
  reachChip.hidden = !lost.length;
  reachChip.disabled = !lost.some((one) => one.fix.length);
  reachChip.textContent = lost.map((one) => one.said).join(" · ");
  reachChip.dataset.tip =
    lost
      .map(
        (one) =>
          one.said +
          (one.fix.length
            ? `\n${t("header.reach.fix", { fix: one.fix.join(" ") })}`
            : "") +
          (one.retryAt
            ? `\n${t("header.reach.next", { next: left(one.retryAt - Date.now()) })}`
            : ""),
      )
      .join("\n") + `\n${t("header.reach.clears")}`;
}

/** @param {import("../core/store.js").Chip} one */
const roleChip = (one) => {
  const node = make(
    one.verb ? "button" : "span",
    one.verb ? "chip stalled" : "chip",
    t(one.key),
  );
  if (one.verb) node.dataset.verb = one.verb;
  node.dataset.tip = t(`${one.key}.tip`, one.at ? { since: full(one.at) } : {});
  return node;
};

function renderRoleChips() {
  roleChips.replaceChildren(...store.chips().map(roleChip));
}

export function render() {
  renderJobs();
  renderReach();
  renderRoleChips();
}

/** @param {(verb: string) => void} press */
export const onVerbClick = (press) => {
  reachChip.onclick = () => press(reachChip.dataset.verb ?? "");
  roleChips.onclick = (event) => {
    const verb = /** @type {HTMLElement} */ (event.target).closest(
      "[data-verb]",
    );
    if (verb instanceof HTMLElement) press(verb.dataset.verb ?? "");
  };
};

/** @param {() => void} open */
export const onJobsClick = (open) => {
  jobsChip.onclick = open;
};
