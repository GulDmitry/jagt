// What the board's marks mean, as a section of the help report: every row renders the page's OWN element.

import {make, span} from '../core/dom.js';
import {t} from '../core/i18n.js';
import {cardRows} from './legend/card.js';
import {pageRows} from './legend/page.js';

export function node() {
  return make('div', 'legend', '', span('legend-head', t('legend.head')),
    ...[...cardRows(), ...pageRows()].flatMap((samples, row) => {
      // A sample button is a real one, and one that answers a click with nothing is worse than none.
      const key = Object.assign(make('div', 'legend-key', '', ...samples), {inert: true});
      return [key, span('legend-says', t(`legend.row.${row}`))];
    }));
}
