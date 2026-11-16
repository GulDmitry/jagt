import { Card } from "../components/Card.js";
import { html } from "../components/html.js";
import { cards } from "./cards.js";
import { State } from "./State.js";

export const cardSection = html`<section aria-label="Card">
  <h2>Card</h2>
  <main>
    ${cards.map(({ name, props }) => html`<${State} name=${name}><${Card} ...${props}/></${State}>`)}
  </main>
</section>`;
