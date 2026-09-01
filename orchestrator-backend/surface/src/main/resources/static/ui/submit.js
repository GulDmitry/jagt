// Every form asks the backend for one thing and says so the same way, so a fix reaches all of them at once.

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
    if (done) done(result);
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
