import {make} from '../core/dom.js';
import {t} from '../core/i18n.js';
import {withSeconds} from '../core/time.js';

// A toast is gone in seconds, so every one is kept here for the session; the log file keeps what matters longer.

const toasts = document.getElementById('toasts');
const opener = document.getElementById('show-log');
const messages = [];

export const sessionLog = () => messages.join('\n');

// The dialog is not this module's to own, so the click is handed over.
export const showLog = (open) => { opener.onclick = open; };

export function toast(message, isError) {
  messages.push(`${withSeconds(Date.now())}  ${message}`);
  opener.hidden = false;
  opener.dataset.tip = t('toast.log.tip', {message});

  // An error interrupts a screen reader; the rest wait their turn in the status region.
  const dismiss = Object.assign(make('button', isError ? 'toast error' : 'toast', message), {type: 'button'});
  const node = make('div', '', '', dismiss);
  if (isError) node.setAttribute('role', 'alert');
  dismiss.onclick = () => node.remove();
  toasts.append(node);
  setTimeout(() => node.remove(), isError ? 12000 : 7000);
}
