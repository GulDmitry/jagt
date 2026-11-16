import { refusal } from "../core/api.js";
import { toast } from "./toast.js";

// The board is read again after every form.
const held = {
  afterwards: async () => {},
};

export const onSent = (read) => {
  held.afterwards = read;
};

export async function sending(form, { waiting, send, done, problem }) {
  const state = form.querySelector(".state");
  const button = form.querySelector("button[type=submit]");
  // requestSubmit() submits past a disabled button.
  if (button.disabled) return;
  const refused = problem?.();
  state?.classList.toggle("bad", !!refused);
  if (refused) {
    state.textContent = refused;
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

export const submits = (form, options) => {
  form.onsubmit = (event) => {
    event.preventDefault();
    return sending(form, options);
  };
};
