// Fetched in one call and repainted once: every action ends here, so a refusal is read against a fresh view.

import {api} from '../core/api.js';
import * as store from '../core/store.js';
import {render} from './render.js';
import {toast} from './toast.js';

let asked = 0;
let painted = 0;

export async function refresh() {
  const mine = ++asked;
  try {
    const data = await api('/api/tasks');
    // Reads overlap, and one answered late describes the board before a later one.
    if (mine < painted) return;
    painted = mine;
    store.set({
      tasks: data.tasks,
      projects: data.projects || [],
      branchStrategies: data.branchStrategies || [],
      phases: data.phases || [],
      autoReview: {summary: data.autoReview, enabled: data.autoReviewEnabled},
      jobs: data.jobs,
      offers: data.offers || {},
    });
    render();
  } catch (e) {
    toast(`Cannot reach the backend: ${e.message}`, true);
  }
}

let verbsFailed = false;

// Fetched, not hardcoded: the palette completes and validates against the server's own verb list. Without it
// the palette degrades rather than breaks, said once per failing streak rather than on every reconnect.
export async function refreshVerbs() {
  try {
    store.set({verbs: await api('/api/commands')});
    verbsFailed = false;
  } catch (e) {
    store.set({verbs: []});
    if (!verbsFailed) toast(`Cannot read the commands: ${e.message}`, true);
    verbsFailed = true;
  }
}
