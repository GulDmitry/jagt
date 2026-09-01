// Fetched in one call and repainted once: every action ends here, so a refusal is read against a fresh view.

import {api} from '../core/api.js';
import * as store from '../core/store.js';
import {render} from './render.js';
import {toast} from './toast.js';

export async function refresh() {
  try {
    const data = await api('/api/tasks');
    store.set({
      tasks: data.tasks,
      projects: data.projects || [],
      branchStrategies: data.branchStrategies || [],
      phases: data.phases || [],
      autoReview: {summary: data.autoReview, enabled: data.autoReviewEnabled},
      jobs: data.jobs,
    });
    render();
  } catch (e) {
    toast(`Cannot reach the backend: ${e.message}`, true);
  }
}

// Fetched, not hardcoded: the palette completes and validates against the server's own verb list.
export async function refreshVerbs() {
  try {
    store.set({verbs: await api('/api/commands')});
  } catch (e) {
    store.set({verbs: []});          // no suggestions is a degraded palette, not a broken one
  }
}
