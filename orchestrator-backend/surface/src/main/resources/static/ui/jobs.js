// The jobs table inside the Jobs report: read when the report opens, each row's Run now sent as `run <job>`.

import {render} from 'preact';
import {api, refusal} from '../core/api.js';
import {html} from '../components/html.js';
import {JobsTable} from '../components/JobsTable.js';
import {toast} from './toast.js';

export function node() {
  const holder = document.createElement('div');
  holder.className = 'jobs-table';
  const paint = () => api('/api/jobs')
    .then((jobs) => render(html`<${JobsTable} jobs=${jobs} now=${Date.now()}/>`, holder))
    .catch((e) => toast(refusal(e), true));
  holder.addEventListener('reread', paint);
  holder.onclick = async (event) => {
    const button = event.target.closest('button[data-job]');
    if (!button) return;
    try {
      toast((await api(`/api/commands/run?about=${encodeURIComponent(button.dataset.job)}`, {method: 'POST'})).message);
    } catch (e) {
      toast(refusal(e), true);
    }
    paint();
  };
  paint();
  return holder;
}
