import { api } from "../core/api.js";
import { submitButton } from "../core/dom.js";
import { t } from "../core/i18n.js";
import { clear, read, watchPath } from "./projectcheck.js";
import { save } from "./projectsave.js";

const dialog = /** @type {HTMLDialogElement} */ (
  document.getElementById("project-dialog")
);
const form = /** @type {HTMLFormElement} */ (
  document.getElementById("project-form")
);
const path = /** @type {HTMLInputElement} */ (
  document.getElementById("project-path")
);
/** @type {{ editing: string | null }} */
const state = {
  editing: null,
};

/** @param {string | null} [key] */
const reset = (key = null) => {
  form.reset();
  form
    .querySelectorAll("[data-typed]")
    .forEach((field) => field.removeAttribute("data-typed"));
  clear();
  state.editing = key;
  /** @type {HTMLElement} */ (
    document.getElementById("project-title")
  ).textContent = key ? t("project.edit", { key }) : t("project.add");
  submitButton(form).textContent = t(
    key ? "project.submit.save" : "project.add-submit",
  );
  /** @type {HTMLInputElement} */ (
    document.getElementById("project-key")
  ).readOnly = !!key;
};

export const open = () => {
  if (!dialog.open) dialog.showModal();
  path.focus();
};

/** @param {string} key */
async function edit(key) {
  const entry = await api(`/api/projects/${encodeURIComponent(key)}`);
  reset(key);
  Object.entries({
    ...entry,
    verify: (entry.verifyCommand || []).join(" "),
    labels: (entry.labels || []).join(", "),
    humanPaths: (entry.humanPaths || []).join(", "),
  }).forEach(([name, value]) => {
    const field = form.elements.namedItem(name);
    if (field instanceof HTMLElement)
      Object.assign(field, { value: value || "" }).dataset.typed = "true";
  });
  open();
  read();
}

/** @param {string} argument */
export async function argued(argument) {
  const named = /^edit\s+(\S+)$/.exec(argument.trim());
  if (named) await edit(named[1]);
  return !!named;
}

// A field typed into is the human's; the reading fills only the rest.
form.addEventListener("input", (event) => {
  if (event.target instanceof HTMLElement && event.target !== path)
    event.target.dataset.typed = "true";
});

document.addEventListener("project-form", (event) => {
  const key = event instanceof CustomEvent ? event.detail?.key : null;
  if (key) edit(key);
  else open();
});

watchPath(() => state.editing);
save({ editing: () => state.editing, done: () => dialog.close() });

/** @type {HTMLElement} */ (document.getElementById("close-project")).onclick =
  () => dialog.close();
dialog.addEventListener("close", () => reset());
