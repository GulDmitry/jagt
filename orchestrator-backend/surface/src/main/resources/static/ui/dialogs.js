// A plain-text report over the board: a <dialog> dims it, cannot be lost behind another window, and closes three ways.

import {api, refusal, text} from '../core/api.js';
import {body} from './report.js';
import {toast} from './toast.js';

const report = document.getElementById('report');
const reportTitle = document.getElementById('report-title');
const reportBody = document.getElementById('report-body');
const reportScroll = document.getElementById('report-scroll');
const reportSection = document.getElementById('report-section');
const reportLine = document.getElementById('report-line');
const say = document.getElementById('say');
const working = document.getElementById('said');

// What the open report is showing, so a change under the reader can be read again and repainted.
let showing = {path: null, about: null, body: ''};
let poll = 0;
// Two reports opened in quick succession: the one asked for last is the one shown, whichever answers last.
let asked = 0;
// A poll and the board's own event read it again side by side, and the read sent last is the one that paints.
let reread = 0;

function paint(title, said, extra) {
  reportTitle.textContent = title;
  reportBody.replaceChildren(...body(said));
  reportSection.replaceChildren(...(extra ? [extra] : []));
  // Only a report about ONE task has a session to talk to, and a line typed at one round is not the next one's.
  reportLine.hidden = !showing.about;
  say.value = '';
  if (!report.open) report.showModal();
  reportScroll.scrollTop = 0;
}

export function showReport(title, said, extra = null) {
  asked++;
  showing = {path: null, about: null, body: said};
  stopWaiting();
  paint(title, said, extra);
}

/** False when the report could not be read, the caller deciding what a failed read costs it. */
export async function openReport(title, path, {extra = null, about = null} = {}) {
  const mine = ++asked;
  try {
    const said = await text(path);
    if (mine !== asked) return true;
    showing = {path, about, body: said};
    stopWaiting();
    paint(title, said, extra);
    // Whatever changed WHILE this was being read reached no open dialog, so it is read once more now.
    await repaintReport();
    return true;
  } catch (e) {
    if (mine !== asked) return true;
    toast(refusal(e), true);
    return false;
  }
}

/** The board changed under an open report: read it again, and stop waiting once it says something new. */
export async function repaintReport() {
  if (!report.open || !showing.path) {
    return;
  }
  const read = showing.path;
  const mine = ++reread;
  let said;
  try {
    said = await text(read);
  } catch (e) {
    return;                       // a read that failed is not an empty report: leave what is on screen
  }
  // The answer to a report nobody is reading any more belongs to no dialog on screen.
  if (mine !== reread || !report.open || showing.path !== read || said === showing.body) {
    return;
  }
  showing = {...showing, body: said};
  const where = reportScroll.scrollTop;
  reportBody.replaceChildren(...body(said));
  reportScroll.scrollTop = where;
  stopWaiting();
}

// A worktree file fires no event, and the agent's own report only comes at the END of a round, so a line just
// said is followed until the answer lands.
const EVERY = 3000;
const GIVE_UP = 300000;

function waitForTheAnswer() {
  stopWaiting();
  working.hidden = false;
  const until = Date.now() + GIVE_UP;
  poll = setInterval(() => (Date.now() > until ? giveUp() : repaintReport()), EVERY);
}

// A ring that just disappears reads as an answer that landed. The board's own event still repaints this.
function giveUp() {
  stopWaiting();
  toast('nothing said back yet — this repaints when it is');
}

function stopWaiting() {
  working.hidden = true;
  if (poll) clearInterval(poll);
  poll = 0;
}

// Sent as typed, and refused by the backend when no session is running: the page decides neither.
reportLine.onsubmit = async (event) => {
  event.preventDefault();
  const line = say.value.trim();
  if (!line) {
    return;
  }
  say.disabled = true;
  try {
    const result = await api(`/api/tasks/say?task=${encodeURIComponent(showing.about)}`, {
      method: 'POST',
      headers: {'Content-Type': 'application/json'},
      body: JSON.stringify({line}),
    });
    toast(result.message);
    say.value = '';
    waitForTheAnswer();
  } catch (e) {
    toast(refusal(e), true);
  } finally {
    say.disabled = false;
    say.focus();
  }
};

// Which element the press STARTED on decides: selecting text inside and releasing outside asks for nothing.
function closeOnBackdrop(dialog) {
  let pressedBackdrop = false;
  dialog.addEventListener('mousedown', (event) => { pressedBackdrop = event.target === dialog; });
  dialog.addEventListener('click', (event) => {
    if (pressedBackdrop && event.target === dialog) dialog.close();
  });
}

document.getElementById('close-report').onclick = () => report.close();
report.addEventListener('close', stopWaiting);
closeOnBackdrop(report);
