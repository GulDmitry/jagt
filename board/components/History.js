import { t } from "../core/i18n.js";
import { originLabel } from "../core/format.js";
import { stamp } from "../core/time.js";
import { html } from "./html.js";

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
            <span class="age">${stamp(step.at, now)}</span>
            ${step.label || step.status}${
              step.origin
                ? html` <span class="muted">${originLabel(step)}</span>`
                : ""
            }
          </li>`,
      )}
    </ol>
  </section>`;
