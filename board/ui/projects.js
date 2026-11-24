import { api, refusal } from "../core/api.js";
import { make, span } from "../core/dom.js";
import { t } from "../core/i18n.js";
import * as store from "../core/store.js";

const group = /** @type {HTMLElement} */ (document.getElementById("project"));
/** @type {{ rendered: string | null }} */
const state = {
  rendered: null,
};

/** @param {string} selector */
const boxes = (selector) =>
  [...group.querySelectorAll(selector)].filter(
    (box) => box instanceof HTMLInputElement,
  );

// The order they were ticked in is not kept, so the CONFIGURED order decides which repository the agent's
export const chosen = () => {
  const ticked = new Set(boxes("input:checked").map((box) => box.value));
  return store
    .projects()
    .filter((project) => ticked.has(project))
    .join(",");
};

export const forget = () => {
  boxes("input").forEach((box) => {
    box.checked = false;
  });
};

/** @param {Record<string, string | string[]>} card */
const said = (card) =>
  Object.entries(card)
    .filter(([, value]) => value?.length)
    .map(([name, value]) => `${name}: ${[value].flat().join(", ")}`)
    .join("\n");

export const cards = () =>
  Promise.all(
    [...group.querySelectorAll("label")].map(async (label) => {
      const box = label.firstElementChild;
      if (!(box instanceof HTMLInputElement)) return;
      const key = box.value;
      label.dataset.tip = await api(
        `/api/projects/${encodeURIComponent(key)}/card`,
      ).then(said, (e) => `${key}: ${refusal(e)}`);
    }),
  );

export function render() {
  const configured = store.projects();
  const signature = configured.join("\n");
  if (state.rendered === signature) {
    return; // a rebuild under a human ticking boxes would drop the ticks
  }
  state.rendered = signature;
  const ticked = new Set(chosen().split(",").filter(Boolean));
  const byName = [...configured].sort((a, b) => a.localeCompare(b, "en"));
  group.replaceChildren(
    ...(byName.length
      ? byName.map((project) => {
          const box = Object.assign(document.createElement("input"), {
            type: "checkbox",
            value: project,
            name: "project",
          });
          box.checked = ticked.has(project);
          return make("label", "checkbox", "", box, ` ${project}`);
        })
      : [span("muted", t("launch.no-projects"))]),
  );
}
