// What opens OVER the board: a plain-text report. A native <dialog> dims the board itself and cannot be lost
// behind the window you were already reading — and it closes three ways, Escape, its button and the dimmed
// area, which is the click a human makes first.

import {text} from '../core/api.js';
import {toast} from './toast.js';

const report = document.getElementById('report');
const reportTitle = document.getElementById('report-title');
const reportBody = document.getElementById('report-body');
const reportScroll = document.getElementById('report-scroll');
const reportSection = document.getElementById('report-section');

// `extra` is a rendered section ABOVE the text: what a colour or a mark means cannot be said in a monospace
// column, and what is already on screen is read before what can be typed. A second dialog for it would be a
// second answer to "how does this work".
export function showReport(title, body, extra = null) {
  reportTitle.textContent = title;
  reportBody.textContent = body.trimEnd();
  reportSection.replaceChildren(...(extra ? [extra] : []));
  if (!report.open) report.showModal();
  reportScroll.scrollTop = 0;
}

export async function openReport(title, path, extra = null) {
  try {
    showReport(title, await text(path), extra);
  } catch (e) {
    toast(e.message, true);
  }
}

// Which element the press STARTED on decides: selecting text inside and releasing outside is not a request to
// close what you were reading.
function closeOnBackdrop(dialog) {
  let pressedBackdrop = false;
  dialog.addEventListener('mousedown', (event) => { pressedBackdrop = event.target === dialog; });
  dialog.addEventListener('click', (event) => {
    if (pressedBackdrop && event.target === dialog) dialog.close();
  });
}

document.getElementById('close-report').onclick = () => report.close();
closeOnBackdrop(report);
