import { httpOnly } from "../core/dom.js";
import { duration } from "../core/format.js";
import { t } from "../core/i18n.js";
import {
  classes,
  marked,
  requestLines,
  statusTip,
  watchLine,
} from "./facts.js";
import { html } from "./html.js";

/** @typedef {import("./facts.js").Stalled} Stalled */
/** @typedef {import("../core/store.js").Task} CardTask */

/** @param {{ url: string, label: string, openedAt?: number, task: CardTask, sole: boolean, now: number }} props */
const RequestChip = ({ url, label, openedAt = 0, task, sole, now }) => {
  const watch = watchLine(task.autoReview, now);
  const lines = requestLines(task, openedAt, watch);
  const approved = sole && task.approved;
  const text = openedAt > 0 ? `${label} ${duration(now - openedAt)}` : label;
  return html`<a
    href=${httpOnly(url)}
    target="_blank"
    rel="noreferrer"
    class=${classes("mr-age", approved && "approved", sole && watch?.stalled && "stalled")}
    data-tip=${lines.join("\n")}
    >${text}${approved ? " ✓" : ""}</a
  >`;
};

// Where several links share one approval there is no label to tick, so it becomes the same glyph on its own.
const ApprovalTick = () =>
  html`<span
    class="tick"
    role="img"
    aria-label=${t("request-approved")}
    tabindex=${0}
    data-tip=${t("request-approved")}
    >✓</span
  >`;

/** @param {{ task: CardTask }} props */
const ChecksDot = ({ task }) => {
  const said = t("meta.checks", {
    said: task.pipelineSaid || task.pipeline.toLowerCase(),
  });
  return html`<span
    class=${`checks ${task.pipeline.toLowerCase()}`}
    role="img"
    aria-label=${said}
    tabindex=${0}
    data-tip=${task.pipelineUnread ? `${said}\n${t("meta.checks.fail")}` : said}
  ></span>`;
};

// The age is INSIDE the status: a bare duration between two separators reads as a fact of its own.
/** @param {{ task: CardTask, stalled: Stalled | null, now: number }} props */
const StatusChip = ({ task, stalled, now }) => {
  const { tip, live } = statusTip(task, stalled);
  return html`<span
    class=${classes("status", stalled && "stalled", live && "live")}
    data-tip=${tip || undefined}
    tabindex=${tip ? 0 : undefined}
    role=${live ? "img" : undefined}
    aria-label=${live ? `${task.statusLabel}, ${t("meta.live")}` : undefined}
    >${task.statusLabel}${" "}
    <span class="age">${duration(now - task.statusSince)}</span></span
  >`;
};

// Each repository named once, its request on its name: a chip beside every name would crowd the row.
/** @param {{ task: CardTask, now: number }} props */
const Repositories = ({ task, now }) =>
  html`<span
    class=${classes("repos", watchLine(task.autoReview, now)?.stalled && "stalled")}
    >${(task.repos || []).map(
      (repo, index) =>
        html`${index ? " + " : ""}${
          repo.reviewRequestUrl
            ? html`<${RequestChip}
                url=${repo.reviewRequestUrl}
                label=${repo.project}
                openedAt=${0}
                task=${task}
                sole=${false}
                now=${now}
              />`
            : repo.project
        }`,
    )}${task.approved ? html` <${ApprovalTick} />` : ""}</span
  >`;

/** @param {{ task: CardTask, manyProjects: boolean, now: number }} props */
export function Meta({ task, manyProjects, now }) {
  const many = (task.repos || []).length > 1;
  // A stopped poll with no request chip to ring rings the state instead.
  const watch = watchLine(task.autoReview, now);
  return html`<div class="meta">
    <${StatusChip}
      task=${task}
      now=${now}
      stalled=${!many && !task.reviewRequestUrl && watch?.stalled ? watch : null}
    />
    ${
      many
        ? html`<${Repositories} task=${task} now=${now} />`
        : html` ${
            task.reviewRequestUrl
              ? html`<${RequestChip}
                  url=${task.reviewRequestUrl}
                  label=${manyProjects ? task.project : t("request")}
                  openedAt=${task.requestOpenedAt}
                  task=${task}
                  sole=${true}
                  now=${now}
                />`
              : manyProjects
                ? html`<span>${task.project}</span>`
                : ""
          }`
    }
    ${marked(task) ? html`<${ChecksDot} task=${task} />` : ""}
  </div>`;
}
