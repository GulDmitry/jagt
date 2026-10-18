// A report's text drawn from `reportParts`: one renderer for the dialog and for the round tab.

import {httpOnly} from '../core/dom.js';
import {html} from './html.js';

const Part = ({part}) => {
  switch (part.kind) {
    case 'link': return html`<a href=${httpOnly(part.href)} target="_blank" rel="noreferrer">${part.text}</a>`;
    case 'verdict': return html`<span class=${`verdict ${part.tone}`}>${part.text}</span>`;
    case 'block':
    case 'quote': return html`<span class=${part.kind}>${part.parts.map((one) => html`<${Part} part=${one}/>`)}</span>`;
    default: return part.text;
  }
};

export const Report = ({parts}) => parts.map((part) => html`<${Part} part=${part}/>`);
