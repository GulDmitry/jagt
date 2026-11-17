import { t } from "../core/i18n.js";
import { dayAndMinutes, minutes, sameDay } from "../core/time.js";
import { html } from "./html.js";

// Today's steps by the clock; older ones by day too.
/**
 * @param {number} at
 * @param {number} now
 */
const when = (at, now) => (sameDay(at, now) ? minutes(at) : dayAndMinutes(at));

/** @param {{ task: import("../core/store.js").Task, now: number }} props */
export const History = ({ task, now }) =>
  html`<section
    class="history"
    role="tabpanel"
    id="panel-overview"
    aria-labelledby="tab-overview"
  >
    <h2 class="group-head">${t("pane.history")}</h2>
    <ol>
      ${[...(task.history || [])].reverse().map(
        (step) =>
          html`<li
            data-tip=${step.why || undefined}
            tabindex=${step.why ? 0 : undefined}
          >
            <span class="age">${when(step.at, now)}</span>
            ${step.label || step.status}${
              step.origin
                ? html` <span class="muted"
                    >${step.origin.toLowerCase().replace("_", "-")}</span
                  >`
                : ""
            }
          </li>`,
      )}
    </ol>
  </section>`;
