import { broken, parse } from "../core/logline.js";
import { html } from "./html.js";

/** @param {{ said: string, empty?: string }} props */
export const LogLines = ({ said, empty }) => {
  const { head, entries } = parse(said);
  const lead = said ? head.join("\n") : empty;
  return html`<div class="log-lines">
    ${lead ? html`<p class="log-head">${lead}</p>` : null}
    ${entries.map(
      ({ seq, time, level, text }) =>
        html`<p
          key=${seq}
          class=${broken(level) ? "log-line danger" : "log-line"}
        >
          <span class="log-time">${time}</span
          ><span class="log-level">${level}</span>${text.join("\n")}
        </p>`,
    )}
  </div>`;
};
