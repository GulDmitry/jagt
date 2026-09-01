// A plain-text report over the board: a <dialog> dims it, cannot be lost behind another window, and closes three ways.

import {text} from '../core/api.js';
import {link} from '../core/dom.js';
import {toast} from './toast.js';

const report = document.getElementById('report');
const reportTitle = document.getElementById('report-title');
const reportBody = document.getElementById('report-body');
const reportScroll = document.getElementById('report-scroll');
const reportSection = document.getElementById('report-section');

// A url a human has to select and copy is one nobody follows. Only http(s) becomes an anchor: this text comes
// from an agent, a model and a hand-edited file, and a `javascript:` URL would run in the page that can deploy.
const WEB_LINK = /https?:\/\/[^\s<>"'`)\]]+/g;

function linked(body) {
  const parts = [];
  let at = 0;
  for (const found of body.matchAll(WEB_LINK)) {
    // A URL ending a sentence keeps that sentence's punctuation out of the href.
    const url = found[0].replace(/[.,;:!?]+$/, '');
    if (found.index > at) parts.push(body.slice(at, found.index));
    parts.push(link(url, url));
    at = found.index + url.length;
  }
  parts.push(body.slice(at));
  return parts;
}

export function showReport(title, body, extra = null) {
  reportTitle.textContent = title;
  reportBody.replaceChildren(...linked(body.trimEnd()));
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

// Which element the press STARTED on decides: selecting text inside and releasing outside asks for nothing.
function closeOnBackdrop(dialog) {
  let pressedBackdrop = false;
  dialog.addEventListener('mousedown', (event) => { pressedBackdrop = event.target === dialog; });
  dialog.addEventListener('click', (event) => {
    if (pressedBackdrop && event.target === dialog) dialog.close();
  });
}

document.getElementById('close-report').onclick = () => report.close();
closeOnBackdrop(report);
