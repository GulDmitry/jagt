// Everything that opens OVER the board: a plain-text report, and the agent's own terminal. A native <dialog>
// dims the board itself and cannot be lost behind the window you were already reading — and it closes three
// ways, Escape, its button and the dimmed area, which is the click a human makes first.

import {api, refusal, text} from '../core/api.js';
import {toast} from './toast.js';

const report = document.getElementById('report');
const reportTitle = document.getElementById('report-title');
const reportBody = document.getElementById('report-body');
const reportScroll = document.getElementById('report-scroll');
const reportSection = document.getElementById('report-section');
const terminalDialog = document.getElementById('terminal');
const terminalFrame = document.getElementById('terminal-frame');

// `extra` is a rendered section under the text: what a colour or a mark MEANS cannot be said in a monospace
// column, and a second dialog for it would be a second answer to "how does this work".
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

// Focus, rendered rather than announced: the action selects the agent's tmux window whatever surface asked, and
// when a web terminal serves that session the board shows it right here. With none configured there is nothing
// to open and the sentence in the toast — which window the session is in — is the whole answer.
export async function openTerminal(task) {
  let port;
  try {
    ({port} = await api(`/api/tasks/terminal?task=${encodeURIComponent(task.id)}`, {method: 'POST'}));
  } catch (e) {
    toast(refusal(e), true);      // no port is silence; a refusal is not, or a gone task reads as "not set up"
    return;
  }
  if (!port) return;
  // The server answers with a port only: the host is whatever name this page reached jagt under, and the
  // terminal runs on the same machine — an address chosen there would be jagt's own loopback, not ours.
  const url = `http://${location.hostname}:${port}`;
  document.getElementById('terminal-title').textContent = `${task.alias || task.id} · ${task.id}`;
  // Re-pointing the frame at the same address attaches a second client for nothing.
  if (terminalFrame.getAttribute('src') !== url) terminalFrame.src = url;
  if (!terminalDialog.open) terminalDialog.showModal();
}

document.getElementById('close-report').onclick = () => report.close();
document.getElementById('close-terminal').onclick = () => terminalDialog.close();
closeOnBackdrop(report);
closeOnBackdrop(terminalDialog);
// A loaded frame stays attached, and tmux sizes every window to its smallest client — including one nobody
// is looking at.
terminalDialog.addEventListener('close', () => { terminalFrame.src = 'about:blank'; });
