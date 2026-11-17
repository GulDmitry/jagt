import { html } from "../components/html.js";

/** @param {{ name: string, children?: import("preact").ComponentChildren }} props */
export const State = ({ name, children }) =>
  html`<figure class="state" data-state=${name}>
    <figcaption class="muted">${name}</figcaption>
    ${children}
  </figure>`;
