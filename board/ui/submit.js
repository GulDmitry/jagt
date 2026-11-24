import { refusal } from "../core/api.js";
import { submitButton } from "../core/dom.js";
import { toast } from "./toast.js";

/** @typedef {{ waiting: string, send: () => Promise<{ message: string }>, done?: (result: { message: string }) => void, problem?: () => string | null, inline?: (failure: unknown) => void }} Sending */

/** @type {{ afterwards: () => Promise<void> }} */
const held = {
  afterwards: async () => {},
};

/** @param {() => Promise<void>} read */
export const onSent = (read) => {
  held.afterwards = read;
};

/**
 * @param {unknown} failure
 * @param {Element | null} state
 * @param {Sending["inline"]} inline
 * @returns {boolean} whether the reason stays in the form
 */
const refused = (failure, state, inline) => {
  if (!inline || !state) {
    toast(refusal(failure), true);
    return false;
  }
  state.textContent = refusal(failure);
  state.classList.add("bad");
  inline(failure);
  return true;
};

/**
 * @param {HTMLFormElement} form
 * @param {Sending} options
 */
export async function sending(form, { waiting, send, done, problem, inline }) {
  const state = form.querySelector(".state");
  const button = submitButton(form);
  // requestSubmit() submits past a disabled button.
  if (button.disabled) return;
  const blocked = problem?.();
  state?.classList.toggle("bad", !!blocked);
  if (blocked) {
    if (state) state.textContent = blocked;
    return;
  }
  button.disabled = true;
  if (state) state.textContent = waiting;
  let kept = false;
  try {
    const result = await send();
    toast(result.message);
    if (done) done(result);
  } catch (e) {
    kept = refused(e, state, inline);
  } finally {
    button.disabled = false;
    if (state && !kept) state.textContent = "";
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
