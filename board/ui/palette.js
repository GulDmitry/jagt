import { interpret } from "./interpret.js";
import { boardArrived, verbsArrived } from "./refresh.js";
import { routeLine } from "./route.js";
import { tier1 } from "./tier1.js";
import { ask, judge } from "./verdict.js";

const form = /** @type {HTMLFormElement} */ (
  document.getElementById("palette")
);
const opener = /** @type {HTMLElement} */ (
  document.getElementById("open-palette")
);

/** @typedef {{ partFor: (part?: string) => import("./execute.js").Part }} Forms */

// Asked here, decided at wiring time, so this module knows no form and no verb by name.
/** @type {{ forms: Forms }} */
const state = {
  forms: { partFor: () => ({}) },
};

/** @param {Forms} wired */
export const wire = (wired) => {
  state.forms = wired;
};

export const isOpen = () => !form.hidden;

/** @param {boolean} open */
function show(open) {
  form.hidden = !open;
  opener.setAttribute("aria-pressed", String(open));
  if (open) {
    ask.focus();
    ask.select();
  }
}

export const close = () => {
  const wasOpen = isOpen();
  show(false);
  return wasOpen;
};

export function toggle() {
  show(Boolean(form.hidden));
  judge();
}

const clear = () => {
  ask.value = "";
  close();
};

ask.addEventListener("input", judge);
opener.onclick = toggle;

form.onsubmit = async (event) => {
  event.preventDefault();
  await Promise.all([verbsArrived, boardArrived]);
  const { route, parsed } = routeLine(ask.value);
  if (route === "judge") return;
  if (route === "run" && parsed) {
    await tier1(parsed, { form, partFor: state.forms.partFor, clear });
    return;
  }
  await interpret(form, ask.value, clear);
};
