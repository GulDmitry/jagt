import { Card } from "./Card.js";
import { reportParts, switchLines } from "../core/format.js";
import { t } from "../core/i18n.js";
import { html } from "./html.js";
import { Report } from "./Report.js";
import { draw } from "../ui/mermaid.js";
import { dayAndMinutes, minutes, sameDay } from "../core/time.js";

export const TABS = [
  { id: "overview", label: t("tab.overview") },
  { id: "round", label: t("tab.round") },
  { id: "artifacts", label: t("tab.artifacts") },
  { id: "code", label: t("tab.code") },
  { id: "diff", label: t("tab.diff") },
  { id: "session", label: t("tab.session") },
];

// Today's steps by the clock; older ones by day too.
const when = (at, now) => (sameDay(at, now) ? minutes(at) : dayAndMinutes(at));

const History = ({ task, now }) =>
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

const Round = ({ shown, offered }) =>
  html`<section
    class="round"
    role="tabpanel"
    id="panel-round"
    aria-labelledby="tab-round"
  >
    ${
      !offered
        ? html`<p class="muted">${t("pane.nothing-drafted")}</p>`
        : shown === null
          ? html`<pre class="screen">${t("reading")}</pre>`
          : html`<pre
              class="screen"
            ><${Report} parts=${reportParts(shown)}/></pre>`
    }
  </section>`;

const Artifacts = ({ task, shown }) =>
  html`<section
    class="artifacts"
    role="tabpanel"
    id="panel-artifacts"
    aria-labelledby="tab-artifacts"
  >
    ${switchLines(task).length ? html`<p class="muted switches">${switchLines(task).map((line, at) => html`${at ? html`<br />` : ""}${line}`)}</p>` : ""}
    ${
      shown === null
        ? html`<p class="muted">${t("reading")}</p>`
        : !shown.names.length
          ? html`<p class="muted">${t("pane.no-doc")}</p>`
          : html`<div class="doc-names">
                ${shown.names.map(
                  (name) =>
                    html`<button
                      data-file=${name}
                      data-task=${task.id}
                      aria-pressed=${String(name === shown.name)}
                    >
                      ${name}
                    </button>`,
                )}
              </div>
              <div
                class="doc"
                ref=${(root) => draw(root)}
                dangerouslySetInnerHTML=${{ __html: shown.html }}
              ></div>`
    }
  </section>`;

const kind = (line) =>
  /^(\+\+\+|---|diff|index|@@)/.test(line)
    ? "muted"
    : line[0] === "+"
      ? "changed"
      : line[0] === "-"
        ? "removed"
        : "";

const Diff = ({ shown }) =>
  html`<section
    class="diff"
    role="tabpanel"
    id="panel-diff"
    aria-labelledby="tab-diff"
  >
    ${
      shown === null
        ? html`<p class="muted">${t("reading")}</p>`
        : typeof shown === "string" || !shown.length
          ? html`<p class="muted">${shown || t("pane.no-diff")}</p>`
          : shown.map(
              (repo) =>
                html`<h2 class="group-head">${repo.project} · ${repo.base}</h2>
                  <pre class="screen">
${repo.diff ? repo.diff.split("\n").map((line) => html`<span class=${kind(line)}>${line} </span>`) : t("pane.no-diff")}</pre>
                  ${repo.cut ? html`<p class="muted">${t("pane.diff-cut")}</p>` : ""}`,
            )
    }
  </section>`;

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
    ${
      tab === "session"
        ? html`<${Session} task=${card.task} shown=${shown} />`
        : tab === "artifacts"
          ? html`<${Artifacts} task=${card.task} shown=${shown} />`
          : tab === "code"
            ? shown?.Code
              ? html`<${shown.Code} key=${card.task.id} task=${card.task} />`
              : html`<p class=${shown ? "detail problem" : "muted"}>
                  ${shown ? shown.failure : t("reading")}
                </p>`
            : tab === "diff"
              ? html`<${Diff} shown=${shown} />`
              : tab === "round"
                ? html`<${Round}
                    shown=${shown}
                    offered=${card.offers.some((offer) => offer.part === "round")}
                  />`
                : html`<${History} task=${card.task} now=${card.now} />`
    }
    <${Dock}
      key=${card.task.id}
      task=${card.task}
      named=${tab === "session" && shown ? shown.name : null}
      held=${tab === "session" ? (shown ? Boolean(shown.name) || shown.held : null) : peek.held}
      line=${tab !== "session" && card.task.owner === "AGENT" && peek.held ? peek.line : ""}
    />
  </section>`;
