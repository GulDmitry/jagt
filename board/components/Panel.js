import { t } from "../core/i18n.js";
import { History } from "./History.js";
import { html } from "./html.js";
import { Artifacts, Diff, Round } from "./Reading.js";
import { Session } from "./Session.js";

/**
 * @typedef {import("./Pane.js").CardTask} CardTask
 * @typedef {import("./Pane.js").PaneCard} PaneCard
 * @typedef {import("./Pane.js").Reading} Reading
 * @typedef {import("./Pane.js").Shown} Shown
 */

/** @param {{ task: CardTask, named: string | null | undefined, line: string | undefined, off: boolean, canRun: boolean }} props */
export const Dock = ({ task, named, line, off, canRun }) =>
  html`<div class="dock">
    ${line && html`<div class="peek muted">${line}</div>`}
    <form class="say" data-say=${task.id} data-window=${named || undefined}>
      <input
        name="line"
        aria-label=${t("say.label")}
        placeholder=${t(off ? "say.none" : "say.hint")}
        autocomplete="off"
        disabled=${off}
        required
      />
      <button type="submit" disabled=${off}>${t("say.send")}</button>
      ${
        canRun &&
        html`<button type="submit" data-run="" disabled=${off}>
          ${t("say.run")}
        </button>`
      }
    </form>
  </div>`;

/** @param {{ task: CardTask, shown: Reading | null }} props */
const CodePane = ({ task, shown }) =>
  shown?.Code
    ? html`<${shown.Code} key=${task.id} task=${task} />`
    : html`<p class=${shown ? "detail problem" : "muted"}>
        ${shown ? shown.failure : t("reading")}
      </p>`;

/** @param {{ card: PaneCard, tab: string, shown: Shown }} props */
export const Panel = ({ card, tab, shown }) =>
  tab === "session"
    ? html`<${Session} task=${card.task} shown=${shown} />`
    : tab === "artifacts"
      ? html`<${Artifacts} task=${card.task} shown=${shown} />`
      : tab === "code"
        ? html`<${CodePane} task=${card.task} shown=${shown} />`
        : tab === "diff"
          ? html`<${Diff} shown=${shown} />`
          : tab === "round"
            ? html`<${Round}
                shown=${shown}
                offered=${card.offers.some((offer) => offer.part === "round")}
              />`
            : html`<${History} task=${card.task} now=${card.now} />`;
