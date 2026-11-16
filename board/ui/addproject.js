import { api } from "../core/api.js";
import { t } from "../core/i18n.js";
import { clear, read, watchPath } from "./projectcheck.js";
import { save } from "./projectsave.js";

const $ = (id) => document.getElementById(id);
const dialog = $("project-dialog");
const form = $("project-form");
const path = $("project-path");
const state = {
  editing: null,
};

const reset = (key = null) => {
  form.reset();
  form
    .querySelectorAll("[data-typed]")
    .forEach((field) => delete field.dataset.typed);
  clear();
  state.editing = key;
  $("project-title").textContent = key
    ? t("project.edit", { key })
    : t("project.add");
  form.querySelector("button[type=submit]").textContent = t(
    key ? "project.submit.save" : "project.add-submit",
  );
  $("project-key").readOnly = !!key;
};

export const open = () => {
  if (!dialog.open) dialog.showModal();
  path.focus();
};

async function edit(key) {
  const entry = await api(`/api/projects/${encodeURIComponent(key)}`);
  reset(key);
  Object.entries({
    ...entry,
    verify: (entry.verifyCommand || []).join(" "),
    labels: (entry.labels || []).join(", "),
    humanPaths: (entry.humanPaths || []).join(", "),
  }).forEach(([name, value]) => {
    if (form.elements[name])
      Object.assign(form.elements[name], { value: value || "" }).dataset.typed =
        "true";
  });
  open();
  read();
}

export async function argued(argument) {
  const named = /^edit\s+(\S+)$/.exec(argument.trim());
  if (named) await edit(named[1]);
  return !!named;
}

// A field typed into is the human's; the reading fills only the rest.
form.addEventListener("input", (event) => {
  if (event.target !== path) event.target.dataset.typed = "true";
});

watchPath(() => state.editing);
save({ editing: () => state.editing, done: () => dialog.close() });

$("close-project").onclick = () => dialog.close();
dialog.addEventListener("close", () => reset());
