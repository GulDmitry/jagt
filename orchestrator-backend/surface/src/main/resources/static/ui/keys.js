// Every binding in one place. Escape used to be answered in three, and each new panel added a fourth — so it is
// answered ONCE here, doing what the two listeners together used to do: clear the filter if that is where the
// caret is, and close whatever is open.

import * as filters from './filters.js';
import * as palette from './palette.js';
import {render} from './render.js';
import * as resume from './resume.js';

const typingInto = (target) => target instanceof HTMLInputElement || target instanceof HTMLTextAreaElement
  || target instanceof HTMLSelectElement;

document.addEventListener('keydown', (event) => {
  if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 'k') {
    event.preventDefault();
    palette.toggle();
    return;
  }
  if (event.key === '/' && !typingInto(event.target)) {
    event.preventDefault();
    filters.box.focus();
    return;
  }
  // A <dialog> closes on Escape by itself; the inline forms do not.
  if (event.key !== 'Escape') return;
  if (event.target === filters.box) {
    filters.box.value = '';
    render();
  }
  palette.close();
  resume.close();
});
