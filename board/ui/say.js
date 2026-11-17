import { pressKey, refusal, runTo, sayTo, sayToWindow } from "../core/api.js";
import { render } from "./render.js";
import { sending } from "./submit.js";
import * as tabs from "./tabs.js";
import { toast } from "./toast.js";

/**
 * @param {string} task
 * @param {string} key
 */
export const press = (task, key) =>
  pressKey(task, key)
    .then(
      () => tabs.forget(),
      (e) => toast(refusal(e), true),
    )
    .then(render);

/** @type {HTMLElement} */ (document.getElementById("board")).addEventListener(
  "submit",
  (event) => {
    const form =
      event.target instanceof Element
        ? event.target.closest("form[data-say]")
        : null;
    if (!(form instanceof HTMLFormElement)) return;
    event.preventDefault();
    const field = form.elements.namedItem("line");
    const line = field instanceof HTMLInputElement ? field.value.trim() : "";
    if (!line) return;
    const send = form.dataset.window
      ? /** @param {string} name @param {string} said */ (name, said) =>
          sayToWindow(name, said)
      : event.submitter?.hasAttribute("data-run")
        ? runTo
        : sayTo;
    sending(form, {
      waiting: "sending…",
      send: () => send(form.dataset.window || form.dataset.say || "", line),
      done: () => {
        form.reset();
        tabs.forget();
        render();
      },
    });
  },
);
