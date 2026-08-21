// Everything the board knows, fetched in one call and repainted once. Every action ends here, so a refusal is
// read against a view that has already caught up.

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
      autoReview: {summary: data.autoReview, enabled: data.autoReviewEnabled},
      jobs: data.jobs,
    });
    render();
  } catch (e) {
    toast(`Cannot reach the backend: ${e.message}`, true);
  }
}

// Fetched, not hardcoded: the palette completes and validates against the SERVER's verb list, so a command the
// console accepts can never be missing from the suggestions here.
export async function refreshVerbs() {
  try {
    store.set({verbs: await api('/api/commands')});
  } catch (e) {
    store.set({verbs: []});          // no suggestions is a degraded palette, not a broken one
  }
}
