import { refusal } from "../core/api.js";
import { submitButton } from "../core/dom.js";
import { toast } from "./toast.js";

/** @typedef {{ waiting: string, send: () => Promise<{ message: string }>, done?: (result: { message: string }) => void, problem?: () => string | null }} Sending */

/** @type {{ afterwards: () => Promise<void> }} */
const held = {
  afterwards: async () => {},
};

/** @param {() => Promise<void>} read */
export const onSent = (read) => {
  held.afterwards = read;
};

/**
 * @param {HTMLFormElement} form
 * @param {Sending} options
 */
export async function sending(form, { waiting, send, done, problem }) {
  const state = form.querySelector(".state");
  const button = submitButton(form);
  // requestSubmit() submits past a disabled button.
  if (button.disabled) return;
  const refused = problem?.();
  state?.classList.toggle("bad", !!refused);
  if (refused) {
    if (state) state.textContent = refused;
    return;
  }
  button.disabled = true;
  if (state) state.textContent = waiting;
  try {
    const result = await send();
    toast(result.message);
    if (done) done(result);
  } catch (e) {
    toast(refusal(e), true);
  } finally {
    button.disabled = false;
    if (state) state.textContent = "";
    await held.afterwards();
  }
}

/**
 * @param {HTMLFormElement} form
 * @param {Sending} options
 */
export const submits = (form, options) => {
  form.onsubmit = (event) => {
    event.preventDefault();
    return sending(form, options);
  };
};
