import { closest, make, span } from "../core/dom.js";
import * as store from "../core/store.js";

const toggle = /** @type {HTMLElement} */ (
  document.getElementById("reports-toggle")
);
const list = /** @type {HTMLElement} */ (document.getElementById("reports"));

export const close = () => {
  const wasOpen = !list.hidden;
  list.hidden = true;
  toggle.setAttribute("aria-expanded", "false");
  return wasOpen;
};

const open = () => {
  list.hidden = false;
  toggle.setAttribute("aria-expanded", "true");
  list.querySelector("button")?.focus();
};

/** @type {{ picked: (verb: string) => void }} */
const state = {
  picked: () => {},
};

/** @param {(verb: string) => void} open */
export const onPick = (open) => {
  state.picked = open;
};

export const render = () =>
  list.replaceChildren(
    ...store
      .verbs()
      .filter((verb) => verb.report && !verb.aboutOneTask)
      .map((verb) => {
        const button = make(
          "button",
          "",
          verb.id.charAt(0).toUpperCase() + verb.id.slice(1),
          span("muted", verb.hint),
        );
        button.id = `show-${verb.id}`;
        button.dataset.verb = verb.id;
        return button;
      }),
  );

toggle.onclick = () => (list.hidden ? open() : close());
list.addEventListener("click", (event) => {
  const button = closest(event, "button");
  if (!button) return;
  close();
  state.picked(button.dataset.verb ?? "");
});
document.addEventListener("pointerdown", (event) => {
  if (!list.hidden && !closest(event, ".menu")) close();
});
