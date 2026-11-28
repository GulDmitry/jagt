import { Card } from "../components/Card.js";
import { html } from "../components/html.js";
import { cards } from "./cards.js";
import { State } from "./State.js";

/**
 * @param {string} title
 * @param {{ name: string, props: any }[]} frames
 */
export const cardFrames = (title, frames) =>
  html`<section aria-label=${title}>
    <h2>${title}</h2>
    <main>
      ${frames.map(({ name, props }) => html`<${State} name=${name}><${Card} ...${props}/></${State}>`)}
    </main>
  </section>`;

export const cardSection = cardFrames("Card", cards);
