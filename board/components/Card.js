import { bothNames, stopLines, switchLines } from "../core/format.js";
import { t } from "../core/i18n.js";
import { httpOnly } from "../core/dom.js";
import { html } from "./html.js";
import { Meta } from "./Meta.js";

/**
 * @typedef {{ id: string, label: string, group?: string, hint?: string, key?: string, primary?: boolean, again?: boolean, apart?: boolean, onRow?: boolean }} Action
 * @typedef {{ status: string, at: number, label?: string, why?: string, origin?: string }} Step
 * @typedef {{ project: string, reviewRequestUrl?: string }} TaskRepo
 * @typedef {{ state?: string, note?: string, label?: string, nextPollAt: number }} Watch
 * @typedef {{
 *   id: string, alias?: string, title?: string, project: string, repos?: TaskRepo[],
 *   owner: string, attention: string, ask?: string, hint?: string, detail?: string, detailKind?: string,
 *   tier?: string, ticketUrl?: string, status?: string, statusLabel: string, statusSince: number,
 *   actions?: Action[], history?: Step[], switches?: Record<string, string>,
 *   stop?: { reason?: string, evidence?: string, next?: string, at: number, triage?: string },
 *   reviewRequestUrl?: string, requestOpenedAt?: number, approved?: boolean | null,
 *   pipeline: string, pipelineSaid?: string, pipelineUnread?: boolean,
 *   autoReview?: Watch | null, waiting?: string, deployed?: boolean
 * }} CardTask
 */

/** @param {{ taskId: string, on: boolean }} props */
const Flag = ({ taskId, on }) =>
  html`<button
    class="flag"
    data-flag=${taskId}
    aria-pressed=${String(on)}
    aria-label=${t("card.flag")}
    data-tip=${t("card.flag.tip")}
  >
    ${on ? "⚑" : "⚐"}
  </button>`;

// A verb drawn apart stays muted at the row's end even where the move names it.
/** @param {Action} action */
const verbClass = (action) =>
  [
    action.primary && !action.apart && "primary",
    action.again && "again",
    action.apart && "apart",
  ]
    .filter(Boolean)
    .join(" ") || undefined;

// Which groups exist, and which comes first, stays the projection's answer; a verb drawn apart ends its row.
/** @param {CardTask} task */
const rows = (task) => {
  /** @type {{ group?: string, items: { action: Action }[] }[]} */
  const grouped = [];
  for (const action of (task.actions || []).filter(
    (each) => each.group !== "session",
  )) {
    let row = grouped.at(-1);
    if (!row || row.group !== action.group) {
      row = { group: action.group, items: [] };
      grouped.push(row);
    }
    row.items.push({ action });
  }
  /** @param {{ action: Action }} item */
  const last = (item) => Number(Boolean(item.action.apart));
  grouped.forEach((row) =>
    row.items.sort((one, other) => last(one) - last(other)),
  );
  return grouped;
};

/** @param {{ task: CardTask, action: Action, blocked: Set<string> }} props */
const Button = ({ task, action, blocked }) =>
  html`<button
    class=${verbClass(action)}
    data-tip=${action.hint}
    data-task=${task.id}
    data-action=${action.id}
    disabled=${blocked.has(action.id)}
    data-key=${action.key || undefined}
    aria-label=${action.key ? action.label : undefined}
  >
    ${action.label}
  </button>`;

// A problem is broken whatever the tier says; a move of theirs drops its colour with the tier.
/** @param {CardTask} task */
const detailClass = (task) => {
  if (task.detailKind === "PROBLEM") return "detail problem";
  return task.detailKind === "YOURS" && task.attention !== "OPTIONAL"
    ? "detail you"
    : "detail";
};

/** @param {CardTask} task */
const titleLines = (task) => [
  ...(task.tier ? [t("card.tier.tip", { model: task.tier })] : []),
  ...switchLines(task),
];

// `blocked` holds the ids of the verbs a press in flight refuses; `now` the clock every age on it is read against.
/** @param {{ task: CardTask, manyProjects: boolean, flagged: boolean, blocked: Set<string>, now: number }} props */
export function Card({ task, manyProjects, flagged, blocked, now }) {
  const owner = task.owner.toLowerCase();
  const ticket = httpOnly(task.ticketUrl);
  // The badge's words name the ACT and the tier only colours it, both the server's, so badge, count and filter agree.
  return html`<article
    class=${task.attention === "OPTIONAL" ? `${owner} optional` : owner}
    aria-label=${bothNames(task)}
    data-task=${task.id}
  >
    <div class="card-top">
      <span class="alias">${task.alias || "-"}</span>
      ${
        task.ticketUrl
          ? html`<a href=${ticket} target="_blank" rel="noreferrer" class="id"
              >${task.id}</a
            >`
          : html`<span class="id">${task.id}</span>`
      }
      ${
        task.ask
          ? html`<span
              class=${`badge ${task.attention.toLowerCase()}${task.detailKind === "PROBLEM" ? " problem" : ""}`}
              data-tip=${[task.hint, ...stopLines(task)].join("\n")}
              tabindex=${0}
              >${task.ask}</span
            >`
          : ""
      }
    </div>
    <div class="heading">
      <${Flag} taskId=${task.id} on=${flagged} />
      <div class="title" data-tip=${titleLines(task).join("\n") || undefined}>
        ${task.title || ""}
      </div>
    </div>
    <${Meta} task=${task} manyProjects=${manyProjects} now=${now} />
    ${task.detail ? html`<div class=${detailClass(task)}>${task.detail}</div>` : ""}
    ${rows(task).map(
      ({ group, items }) =>
        html`<div class=${`actions ${group}`} data-group=${group}>
          ${items.map(
            ({ action }) =>
              html`<${Button}
                task=${task}
                action=${action}
                blocked=${blocked}
              />`,
          )}
        </div>`,
    )}
  </article>`;
}
