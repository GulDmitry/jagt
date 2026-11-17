import { t } from "../core/i18n.js";
import { html } from "./html.js";

/**
 * @typedef {import("./Pane.js").CardTask} CardTask
 * @typedef {import("./Pane.js").Window} Window
 */

const KEYS = [
  ["ESCAPE", "Esc"],
  ["UP", "↑"],
  ["DOWN", "↓"],
  ["LEFT", "←"],
  ["RIGHT", "→"],
  ["TAB", "Tab"],
  ["SPACE", "Space"],
  ["ENTER", "Enter"],
  ...[1, 2, 3, 4, 5, 6, 7, 8, 9].map((n) => [`DIGIT_${n}`, `${n}`]),
  ["CTRL_C", "^C"],
  ["CTRL_U", "^U"],
];

/** @param {{ task: CardTask, shown: Window | null }} props */
export const Session = ({ task, shown }) =>
  html`<section
    class="session"
    role="tabpanel"
    id="panel-session"
    aria-labelledby="tab-session"
  >
    ${
      shown && shown.names && shown.names.length
        ? html`<div
            class="keys windows"
            role="group"
            aria-label=${t("windows.label")}
          >
            <button data-window="" aria-pressed=${!shown.name}>
              ${t("windows.task")}</button
            >${shown.names.map(
              (name) =>
                html`<button
                  data-window=${name}
                  aria-pressed=${shown.name === name}
                >
                  ${name}
                </button>`,
            )}
          </div>`
        : ""
    }
    ${
      shown?.Terminal
        ? html`<${shown.Terminal} task=${task.id} name=${shown.name} />`
        : html`<pre class="screen">
${shown === null ? t("reading") : shown.held ? shown.shown : t("pane.no-window")}</pre>`
    }
    ${
      shown && shown.name
        ? ""
        : html`<div class="keys" role="group" aria-label=${t("keys.session")}>
            ${KEYS.map(
              ([name, cap]) =>
                html`<button data-press=${name} data-task=${task.id}>
                  ${cap}
                </button>`,
            )}
          </div>`
    }
  </section>`;
