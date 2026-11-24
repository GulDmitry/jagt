import { bothNames } from "../core/format.js";
import { httpOnly } from "../core/dom.js";
import {
  actionRows,
  badgeTip,
  detailClass,
  flagTexts,
  titleLines,
  verbClass,
} from "./cardfacts.js";
import { html } from "./html.js";
import { Meta } from "./Meta.js";

/**
 * @typedef {import("../core/store.js").CardTask} CardTask
 * @typedef {import("../core/store.js").Action} Action
 */

/** @param {{ taskId: string, on: boolean }} props */
const Flag = ({ taskId, on }) => {
  const { label, tip } = flagTexts();
  return html`<button
    class="flag"
    data-flag=${taskId}
    aria-pressed=${String(on)}
    aria-label=${label}
    data-tip=${tip}
  >
    ${on ? "⚑" : "⚐"}
  </button>`;
};

/** @param {{ task: CardTask, action: Action, blocked: Set<string>, said?: string }} props */
const Button = ({ task, action, blocked, said }) =>
  html`<button
    class=${verbClass(action, said !== undefined)}
    data-tip=${said === undefined ? action.hint : `${action.hint}\n${said}`}
    data-task=${task.id}
    data-action=${action.id}
    disabled=${blocked.has(action.id)}
    data-key=${action.key || undefined}
    aria-label=${action.key ? action.label : undefined}
  >
    ${action.label}
  </button>`;

/** @param {{ task: CardTask, manyProjects: boolean, flagged: boolean, blocked: Set<string>, quiet?: Map<string, string>, now: number }} props */
export function Card({
  task,
  manyProjects,
  flagged,
  blocked,
  quiet = new Map(),
  now,
}) {
  const owner = task.owner.toLowerCase();
  const ticket = httpOnly(task.ticketUrl);
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
              data-tip=${badgeTip(task)}
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
    ${actionRows(task).map(
      ({ group, items }) =>
        html`<div class=${`actions ${group}`} data-group=${group}>
          ${items.map(
            ({ action }) =>
              html`<${Button}
                task=${task}
                action=${action}
                blocked=${blocked}
                said=${quiet.get(action.id)}
              />`,
          )}
        </div>`,
    )}
  </article>`;
}
