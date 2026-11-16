import { pressKey, refusal, runTo, sayTo, sayToWindow } from "../core/api.js";
import { render } from "./render.js";
import { sending } from "./submit.js";
import * as tabs from "./tabs.js";
import { toast } from "./toast.js";

export const press = (task, key) =>
  pressKey(task, key)
    .then(
      () => tabs.forget(),
      (e) => toast(refusal(e), true),
    )
    .then(render);

document.getElementById("board").addEventListener("submit", (event) => {
  const form = event.target.closest("form[data-say]");
  if (!form) return;
  event.preventDefault();
  const line = form.elements.line.value.trim();
  if (!line) return;
  const send = form.dataset.window
    ? (name, said) => sayToWindow(name, said)
    : event.submitter?.hasAttribute("data-run")
      ? runTo
      : sayTo;
  sending(form, {
    waiting: "sending…",
    send: () => send(form.dataset.window || form.dataset.say, line),
    done: () => {
      form.reset();
      tabs.forget();
      render();
    },
  });
});
