import { broken, parse } from "../core/logline.js";
import { html } from "./html.js";

/** @param {{ said: string }} props */
export const LogLines = ({ said }) => {
  const { head, entries } = parse(said);
  return html`<div class="log-lines">
    ${head.length ? html`<p class="log-head">${head.join("\n")}</p>` : null}
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
