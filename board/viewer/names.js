import { t } from "../core/i18n.js";
import { html } from "../components/html.js";

/**
 * @typedef {import("./read.js").Tree} Tree
 * @typedef {import("./read.js").Repo} Repo
 * @typedef {import("./read.js").Found} Found
 * @typedef {import("./read.js").Picked} Picked
 * @typedef {(picked: Picked) => void} Pick
 */

/** @param {{ found: Found, picked: Picked | null, project: string, pick: Pick }} props */
const Hits = ({ found, picked, project, pick }) =>
  found.hits.length
    ? found.hits.map(
        (hit) =>
          html`<button
            onClick=${() => pick({ project, path: hit.path, line: hit.line })}
            aria-pressed=${String(picked?.path === hit.path && picked?.line === hit.line)}
          >
            ${hit.path}:${hit.line}
            <span class="muted">${hit.text.trim()}</span>
          </button>`,
      )
    : html`<p class="muted">${t("code.no-hits")}</p>`;

/** @param {{ changes: Repo[] | null, picked: Picked | null, pick: Pick }} props */
const Changes = ({ changes, picked, pick }) => {
  if (changes === null) return html`<p class="muted">${t("reading")}</p>`;
  if (!changes.some((repo) => repo.files.length))
    return html`<p class="muted">${t("pane.no-diff")}</p>`;
  return changes.flatMap((repo) => [
    ...repo.files.map(
      (one) =>
        html`<button
          class="code-change"
          onClick=${() => pick({ project: repo.project, path: one.path, line: 0, change: one })}
          aria-pressed=${String(picked?.project === repo.project && picked?.path === one.path && !!picked.change)}
        >
          ${one.path}
          <span class="muted">${` +${one.added} \u2212${one.removed}`}</span>
        </button>`,
    ),
    repo.cut ? html`<p class="muted">${t("pane.diff-cut")}</p>` : "",
  ]);
};

/** @param {{ trees: Tree[] | null, picked: Picked | null, failure: string, pick: Pick }} props */
const Files = ({ trees, picked, failure, pick }) => {
  if (trees === null)
    return failure ? "" : html`<p class="muted">${t("reading")}</p>`;
  if (!picked) return html`<p class="muted">${t("code.none")}</p>`;
  return trees.flatMap((tree) =>
    tree.files.map(
      (name) =>
        html`<button
          onClick=${() => pick({ project: tree.project, path: name, line: 0 })}
          aria-pressed=${String(picked.project === tree.project && picked.path === name)}
        >
          ${name}
        </button>`,
    ),
  );
};

/**
 * @param {{
 *   found: Found | null,
 *   changed: boolean,
 *   changes: Repo[] | null,
 *   trees: Tree[] | null,
 *   picked: Picked | null,
 *   failure: string,
 *   pick: Pick,
 * }} props
 */
export const Names = ({
  found,
  changed,
  changes,
  trees,
  picked,
  failure,
  pick,
}) => {
  if (found)
    return html`<${Hits}
      found=${found}
      picked=${picked}
      project=${picked?.project || ""}
      pick=${pick}
    />`;
  if (changed)
    return html`<${Changes}
      changes=${changes}
      picked=${picked}
      pick=${pick}
    />`;
  return html`<${Files}
    trees=${trees}
    picked=${picked}
    failure=${failure}
    pick=${pick}
  />`;
};
