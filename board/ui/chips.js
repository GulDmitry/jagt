import { countdown } from "../core/format.js";
import { t } from "../core/i18n.js";
import * as store from "../core/store.js";
import { full } from "../core/time.js";

const jobsChip = document.getElementById("jobs-pulse");
const reachChip = document.getElementById("reach-lost");
const actChip = document.getElementById("act-stopped");

const left = (due) => (due > 0 ? countdown(due) : t("due"));

// Only a failed run is news: the next one sits in the hover.
function renderJobs() {
  const jobs = store.jobs();
  jobsChip.hidden = !jobs || !jobs.failing;
  if (jobsChip.hidden) {
    return;
  }
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

function renderStop() {
  const stop = store.actStop();
  const board = stop?.cause === "BOARD";
  actChip.hidden = !stop;
  actChip.dataset.verb = board ? "unstop" : "act";
  actChip.textContent = t(board ? "board-stopped" : "act-stopped");
  actChip.dataset.tip = !stop
    ? ""
    : board
      ? t("header.board-stop", { since: full(stop.at) })
      : t("header.act-stop");
}

export function render() {
  renderJobs();
  renderReach();
  renderStop();
}

export const onVerbClick = (press) => {
  [actChip, reachChip].forEach((chip) => {
    chip.onclick = () => press(chip.dataset.verb);
  });
};

export const onJobsClick = (open) => {
  jobsChip.onclick = open;
};
