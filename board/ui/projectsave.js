import { api } from "../core/api.js";
import { t } from "../core/i18n.js";
import { sending } from "./submit.js";

const form = document.getElementById("project-form");

const said = (name) => form.elements[name].value.trim();
const list = (name) => {
  const each = said(name)
    .split(",")
    .map((one) => one.trim())
    .filter(Boolean);
  return each.length ? each : null;
};

/** The form adds a project, or saves `editing()`'s; `done` runs once the backend took it. */
export const save = ({ editing, done }) => {
  form.onsubmit = (event) => {
    event.preventDefault();
    const key = editing();
    return sending(form, {
      waiting: t(key ? "project.saving" : "project.adding"),
      send: () =>
        api(
          key ? `/api/projects/${encodeURIComponent(key)}` : "/api/projects",
          {
            method: key ? "PUT" : "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
              key: said("key"),
              path: said("path"),
              baseBranch: said("baseBranch"),
              deployBranch: said("deployBranch") || null,
              verifyCommand: said("verify")
                ? said("verify").split(/\s+/)
                : null,
              about: said("about") || null,
              labels: list("labels"),
              humanPaths: list("humanPaths"),
            }),
          },
        ),
      done,
    });
  };
};
