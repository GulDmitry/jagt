import { t } from "../core/i18n.js";
import { html } from "./html.js";
import { ago, dot, next, tables, verdict } from "./jobfacts.js";

/** @typedef {import("./jobfacts.js").Job} Job */

/** @param {{ job: Job, now: number }} props */
const Row = ({ job, now }) =>
  html`<tr
    class=${job.lastError ? "failed" : undefined}
    data-tip=${t("jobs.next", { when: next(job, now) })}
  >
    <td>
      <span
        class=${`checks ${dot(job)}`}
        role="img"
        aria-label=${verdict(job)}
      ></span>
    </td>
    <td class="job-id">${job.id}</td>
    <td>
      ${job.describe}${job.lastError ? html`<div class="detail problem">${job.lastError}</div>` : ""}
    </td>
    <td class=${job.lastError ? "last failed" : "muted"}>
      ${job.running ? t("jobs.running") : ago(job.lastStartedAt, now)}
    </td>
    <td>
      <button
        type="button"
        class="run-now"
        data-job=${job.id}
        disabled=${job.running}
      >
        ${t("jobs.run-now")}
      </button>
    </td>
  </tr>`;

/** @param {{ jobs: Job[], now: number }} props */
export const JobsTable = ({ jobs, now }) =>
  html`${tables(jobs).map(
    ({ group, jobs: rows }) =>
      html` <table class="jobs" aria-label=${group}>
        <caption class="group-head">
          ${group}
        </caption>
        <thead>
          <tr>
            <th></th>
            <th>${t("jobs.col.job")}</th>
            <th>${t("jobs.col.does")}</th>
            <th>${t("jobs.col.last")}</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          ${rows.map((job) => html`<${Row} key=${job.id} job=${job} now=${now} />`)}
        </tbody>
      </table>`,
  )}`;
