import { t } from "../core/i18n.js";
import { html } from "./html.js";

/** @param {{ lines: { ok: boolean, text: string }[] }} props */
export const ProjectChecks = ({ lines }) =>
  html`${lines.map(
    (line) =>
      html`<li class=${line.ok ? "ok" : "bad"}>
        <span
          class=${`checks ${line.ok ? "green" : "red"}`}
          role="img"
          aria-label=${t(line.ok ? "checks.fine" : "checks.not-fine")}
        ></span>
        ${line.text}
      </li>`,
  )}`;
