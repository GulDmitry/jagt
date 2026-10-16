// What reading a repository path found, one line a fact, each fine or not.

import {html} from './html.js';

export const ProjectChecks = ({lines}) => html`${lines.map((line) => html`<li class=${line.ok ? 'ok' : 'bad'}>
  <span class=${`checks ${line.ok ? 'green' : 'red'}`} role="img" aria-label=${line.ok ? 'fine' : 'not fine'}></span>
  ${line.text}</li>`)}`;
