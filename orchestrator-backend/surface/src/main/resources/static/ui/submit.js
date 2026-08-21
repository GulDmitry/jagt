// Every form on the page asks the backend for one thing and says so the same way: the submit button goes dead,
// the slot beside it says what is happening — these calls can cost a model call and take tens of seconds — the
// answer is a toast, and the board is reloaded whatever happened. Written once: it used to be written three
// times, so a fix reached one form and was forgotten in the next.

import {refusal} from '../core/api.js';
import {refresh} from './refresh.js';
import {toast} from './toast.js';

export async function sending(form, {waiting, send, done}) {
  const state = form.querySelector('.state');
  const button = form.querySelector('button[type=submit]');
  button.disabled = true;
  state.textContent = waiting;
  try {
    const result = await send();
    toast(result.message);
    if (done) done();
  } catch (e) {
    toast(refusal(e), true);
  } finally {
    button.disabled = false;
    state.textContent = '';
    await refresh();
  }
}

export const submits = (form, options) => {
  form.onsubmit = (event) => {
    event.preventDefault();
    return sending(form, options);
  };
};
