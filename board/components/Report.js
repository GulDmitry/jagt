import { httpOnly } from "../core/dom.js";
import { reportParts } from "../core/format.js";
import { LogLines } from "./LogLines.js";
import { html } from "./html.js";

/** @typedef {import("../core/format.js").Part} PartData */

/** @param {{ part: PartData }} props */
const Part = ({ part }) => {
  switch (part.kind) {
    case "link":
      return html`<a
        href=${httpOnly(part.href)}
        target="_blank"
        rel="noreferrer"
        >${part.text}</a
      >`;
    case "task":
      return html`<a class="task-link" href="#" data-task=${part.text}
        >${part.text}</a
      >`;
    case "verdict":
      return html`<span class=${`verdict ${part.tone}`}>${part.text}</span>`;
    case "block":
    case "quote":
      return html`<span class=${part.kind}
        >${(part.parts ?? []).map((one) => html`<${Part} part=${one} />`)}</span
      >`;
    default:
      return part.text;
  }
};

/** @param {{ parts: PartData[] }} props */
export const Report = ({ parts }) =>
  parts.map((part) => html`<${Part} part=${part} />`);

/** @param {{ said: string, taskIds: string[], log: boolean }} props */
export const ReportBody = ({ said, taskIds, log }) =>
  log
    ? html`<${LogLines} said=${said} />`
    : html`<${Report} parts=${reportParts(said, taskIds)} />`;
