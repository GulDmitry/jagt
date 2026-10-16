// Each component in each of its states, from the same modules the board renders.

import {render} from 'preact';
import {Card} from '../ui/card.js';
import {html} from '../ui/html.js';
import '../ui/tips.js';
import {cards} from './cards.js';

const State = ({name, children}) => html`<figure class="state" data-state=${name}>
  <figcaption class="muted">${name}</figcaption>${children}</figure>`;

render(html`<section aria-label="Card">
  <h2>Card</h2>
  <main>${cards.map(({name, props}) => html`<${State} name=${name}><${Card} ...${props}/></${State}>`)}</main>
</section>`, document.getElementById('styleguide'));
