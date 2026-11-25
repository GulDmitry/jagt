import { t } from "../core/i18n.js";
import { Card } from "./Card.js";
import { html } from "./html.js";
import { dockOf } from "./panefacts.js";
import { Dock, Panel } from "./Panel.js";

export const TABS = [
  { id: "overview", label: t("tab.overview") },
  { id: "round", label: t("tab.round") },
  { id: "artifacts", label: t("tab.artifacts") },
  { id: "code", label: t("tab.code") },
  { id: "diff", label: t("tab.diff") },
  { id: "session", label: t("tab.session") },
];

/**
 * @typedef {import("../core/store.js").Task} CardTask
 * @typedef {{ task: CardTask, manyProjects: boolean, flagged: boolean, blocked: Set<string>, quiet?: Map<string, string>, now: number }} CardProps
 * @typedef {CardProps & { offers: { part?: string }[] }} PaneCard
 * @typedef {{ name?: string | null, names?: string[], held?: boolean, shown?: string, Terminal?: import("preact").FunctionComponent<{ task: string, name: string | null | undefined }> }} Window
 * @typedef {{ Code?: import("preact").FunctionComponent<{ task: CardTask }>, failure?: string }} Reading
 * @typedef {import("./Reading.js").Doc} Doc
 * @typedef {import("./Reading.js").DiffRepo} DiffRepo
 * @typedef {null | string | DiffRepo[] | Doc | Window | Reading} Shown
 * @typedef {{ held?: boolean, line?: string }} Peek
 */

/** @param {{ card: PaneCard, tab: string, shown: Shown, peek?: Peek, fresh?: boolean }} props */
export const Pane = ({ card, tab, shown, peek = {}, fresh = false }) =>
  html`<section id="pane" aria-label=${t("pane.label")}>
    <button class="back" data-back="">${t("pane.back")}</button>
    <${Card} key=${card.task.id} ...${card} />
    <div role="tablist" class="tabs">
      ${TABS.map(
        (one) =>
          html`<button
            role="tab"
            data-tab=${one.id}
            id=${`tab-${one.id}`}
            class=${one.id === "round" && fresh ? "you" : undefined}
            aria-selected=${String(one.id === tab)}
            aria-controls=${one.id === tab ? `panel-${one.id}` : undefined}
          >
            ${one.label}
          </button>`,
      )}
    </div>
    <${Panel} card=${card} tab=${tab} shown=${shown} />
    <${Dock}
      key=${card.task.id}
      task=${card.task}
      ...${dockOf(tab, card, /** @type {Window | null} */ (shown), peek)}
    />
  </section>`;
