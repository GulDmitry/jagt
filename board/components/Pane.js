import { t } from "../core/i18n.js";
import { Card } from "./Card.js";
import { History } from "./History.js";
import { html } from "./html.js";
import { Artifacts, Diff, Round } from "./Reading.js";

export const TABS = [
  { id: "overview", label: t("tab.overview") },
  { id: "round", label: t("tab.round") },
  { id: "artifacts", label: t("tab.artifacts") },
  { id: "code", label: t("tab.code") },
  { id: "diff", label: t("tab.diff") },
  { id: "session", label: t("tab.session") },
];

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

/**
 * @typedef {import("./Card.js").CardTask} CardTask
 * @typedef {{ task: CardTask, manyProjects: boolean, flagged: boolean, blocked: Set<string>, now: number }} CardProps
 * @typedef {CardProps & { offers: { part?: string }[] }} PaneCard
 * @typedef {{ name?: string, names?: string[], held?: boolean, shown?: string }} Window
 * @typedef {{ Code?: import("preact").FunctionComponent<{ task: CardTask }>, failure?: string }} Reading
 * @typedef {import("./Reading.js").Doc} Doc
 * @typedef {import("./Reading.js").DiffRepo} DiffRepo
 * @typedef {null | string | DiffRepo[] | Doc | Window | Reading} Shown
 * @typedef {{ held?: boolean, line?: string }} Peek
 */

/** @param {{ task: CardTask, shown: Window | null }} props */
const Session = ({ task, shown }) =>
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
    <pre class="screen">
${shown === null ? t("reading") : shown.held ? shown.shown : t("pane.no-window")}</pre>
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

/** @param {{ task: CardTask, held: boolean | null | undefined, named: string | null | undefined, line: string | undefined }} props */
const Dock = ({ task, held, named, line }) => {
  const off = held === false;
  return html`<div class="dock">
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
        !named &&
        (task.actions || []).some((action) => action.id === "run") &&
        html`<button type="submit" data-run="" disabled=${off}>
          ${t("say.run")}
        </button>`
      }
    </form>
  </div>`;
};

/** @param {{ task: CardTask, shown: Reading | null }} props */
const CodePane = ({ task, shown }) =>
  shown?.Code
    ? html`<${shown.Code} key=${task.id} task=${task} />`
    : html`<p class=${shown ? "detail problem" : "muted"}>
        ${shown ? shown.failure : t("reading")}
      </p>`;

/** @param {{ card: PaneCard, tab: string, shown: Shown }} props */
const Panel = ({ card, tab, shown }) =>
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

/**
 * @param {string} tab
 * @param {PaneCard} card
 * @param {Window | null} shown
 * @param {Peek} peek
 */
const dockOf = (tab, card, shown, peek) => {
  const session = tab === "session";
  return {
    named: session && shown ? shown.name : null,
    held: session
      ? shown
        ? Boolean(shown.name) || shown.held
        : null
      : peek.held,
    line: !session && card.task.owner === "AGENT" && peek.held ? peek.line : "",
  };
};

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
