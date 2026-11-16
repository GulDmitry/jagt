import { html } from "../components/html.js";

export const State = ({ name, children }) =>
  html`<figure class="state" data-state=${name}>
    <figcaption class="muted">${name}</figcaption>
    ${children}
  </figure>`;
