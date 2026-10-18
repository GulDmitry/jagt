// What the board's marks mean, as a section of the help report: every row renders the page's OWN element.

import {span} from '../core/dom.js';
import {cardRows} from './legend/card.js';
import {pageRows} from './legend/page.js';

export function node() {
  const section = document.createElement('div');
  section.className = 'legend';
  section.append(span('legend-head', 'what the board’s colours and marks mean'),
    ...[...cardRows(), ...pageRows()].flatMap(([samples, meaning]) => {
      const key = document.createElement('div');
      key.className = 'legend-key';
      // A sample button is a real one, and one that answers a click with nothing is worse than none.
      key.inert = true;
      key.append(...samples);
      return [key, span('legend-says', meaning)];
    }));
  return section;
}
