import { diffLineClass, reportParts, switchLines } from "../core/format.js";
import { t } from "../core/i18n.js";
import { html } from "./html.js";
import { Report } from "./Report.js";

/**
 * @typedef {import("../core/store.js").Task} CardTask
 * @typedef {{ names: string[], name?: string, html: string }} Doc
 * @typedef {{ project: string, base: string, diff?: string, cut?: boolean }} DiffRepo
 */

/** @param {{ shown: string | null, offered: boolean }} props */
export const Round = ({ shown, offered }) =>
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

/** @param {{ task: CardTask, shown: Doc | null }} props */
export const Artifacts = ({ task, shown }) =>
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
                dangerouslySetInnerHTML=${{ __html: shown.html }}
              ></div>`
    }
  </section>`;

/** @param {{ shown: string | DiffRepo[] | null }} props */
export const Diff = ({ shown }) =>
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
${repo.diff ? repo.diff.split("\n").map((line) => html`<span class=${diffLineClass(line)}>${line} </span>`) : t("pane.no-diff")}</pre>
                  ${repo.cut ? html`<p class="muted">${t("pane.diff-cut")}</p>` : ""}`,
            )
    }
  </section>`;
