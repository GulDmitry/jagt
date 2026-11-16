import { render } from "preact";
import { api, refusal } from "../core/api.js";
import { t } from "../core/i18n.js";
import { html } from "../components/html.js";
import { ProjectChecks } from "../components/ProjectChecks.js";

const path = document.getElementById("project-path");
const checks = document.getElementById("project-checks");
const folders = document.getElementById("project-folders");
const branches = document.getElementById("project-branches");
const suggested = {
  key: "project-key",
  baseBranch: "project-base",
  deployBranch: "project-deploy",
};
const SETTLE = 400;
const LISTING = 150;
const state = {
  due: 0,
  listing: 0,
  asked: 0,
  listed: 0,
  own: () => null,
};

export const clear = () => render(null, checks);

const fill = (id, value) => {
  const field = document.getElementById(id);
  if (!field.dataset.typed) field.value = value || "";
};

const option = (value, label) =>
  Object.assign(document.createElement("option"), { value, label });

// Each folder under the last typed one completes the path.
async function offerFolders() {
  const mine = ++state.listed;
  const typed = path.value;
  const slash = typed.lastIndexOf("/");
  if (slash < 0) {
    folders.replaceChildren();
    return;
  }
  try {
    const found = await api(
      `/api/projects/dirs?under=${encodeURIComponent(typed)}`,
    );
    if (mine === state.listed) {
      folders.replaceChildren(
        ...found.map((each) =>
          option(
            `${typed.slice(0, slash + 1)}${each.name}/`,
            each.repository ? t("project.repository") : "",
          ),
        ),
      );
    }
  } catch {
    if (mine === state.listed) folders.replaceChildren();
  }
}

export async function read() {
  const mine = ++state.asked;
  if (!path.value.trim()) {
    clear();
    return;
  }
  const own = state.own();
  try {
    const reading = await api(
      `/api/projects/check?path=${encodeURIComponent(path.value.trim())}${own ? `&own=${encodeURIComponent(own)}` : ""}`,
    );
    if (mine !== state.asked) return;
    render(html`<${ProjectChecks} lines=${reading.lines} />`, checks);
    Object.entries(suggested).forEach(([key, id]) => fill(id, reading[key]));
    fill("project-verify", (reading.verifyCommand || []).join(" "));
    branches.replaceChildren(
      ...(reading.deployBranches || []).map((name) => option(name, "")),
    );
  } catch (e) {
    if (mine === state.asked)
      render(
        html`<${ProjectChecks} lines=${[{ ok: false, text: refusal(e) }]} />`,
        checks,
      );
  }
}

/** Reads the typed path as it settles; `own()` names the project being edited, whose own key is not a clash. */
export const watchPath = (own) => {
  state.own = own;
  path.addEventListener("input", () => {
    clearTimeout(state.listing);
    state.listing = setTimeout(offerFolders, LISTING);
    clearTimeout(state.due);
    state.due = setTimeout(read, SETTLE);
  });
};
