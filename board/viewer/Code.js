import { useEffect, useRef, useState } from "preact/hooks";
import { t } from "../core/i18n.js";
import { html } from "../components/html.js";
import { open as openEditor, reveal } from "./editor.js";
import * as read from "./read.js";

document.head.append(
  Object.assign(document.createElement("link"), {
    rel: "stylesheet",
    href: new URL("./code.css", import.meta.url).href,
  }),
);

/**
 * @typedef {import("./read.js").Tree} Tree
 * @typedef {import("./read.js").Repo} Repo
 * @typedef {import("./read.js").Content} Content
 * @typedef {import("./read.js").Found} Found
 * @typedef {import("./read.js").Picked} Picked
 * @typedef {(picked: Picked) => void} Pick
 */

/**
 * @param {Tree[]} trees
 * @returns {Picked | null}
 */
const firstOf = (trees) => {
  const tree = trees.find((each) => each.files.length);
  if (!tree) return null;
  return {
    project: tree.project,
    path: tree.files.find((name) => /^readme/i.test(name)) || tree.files[0],
    line: 0,
  };
};

/** @type {Record<string, string>} */
const SAID = { BINARY: "code.binary", TOO_LARGE: "code.large" };

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
const Names = ({ found, changed, changes, trees, picked, failure, pick }) => {
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

/** @param {{ task: { id: string } }} props */
export const Code = ({ task }) => {
  const [trees, setTrees] = useState(/** @type {Tree[] | null} */ (null));
  const [picked, setPicked] = useState(/** @type {Picked | null} */ (null));
  const [file, setFile] = useState(/** @type {Content | null} */ (null));
  const [found, setFound] = useState(/** @type {Found | null} */ (null));
  const [mode, setMode] = useState("files");
  const [changes, setChanges] = useState(/** @type {Repo[] | null} */ (null));
  const [failure, setFailure] = useState("");
  const host = useRef(/** @type {HTMLDivElement | null} */ (null));
  const id = task.id;
  /** @param {Error} e */
  const said = (e) => setFailure(e.message);

  useEffect(() => {
    read.tree(id).then((found) => {
      setTrees(found);
      setPicked(firstOf(found));
    }, said);
  }, [task.id]);

  useEffect(() => {
    if (mode !== "changes") return;
    read.changes(id).then((found) => {
      setChanges(found);
      setFailure("");
    }, said);
  }, [mode, task.id]);

  useEffect(() => {
    if (!picked) return;
    setFile(null);
    read.file(id, picked).then(
      (found) => {
        setFile(found);
        setFailure("");
      },
      (e) =>
        picked.change?.gone
          ? setFile({ kind: "TEXT", path: picked.path, text: "" })
          : said(e),
    );
  }, [picked?.project, picked?.path, picked?.change]);

  useEffect(() => {
    if (file?.kind !== "TEXT") return undefined;
    let gone = false;
    /** @type {(() => void) | null} */
    let stop = null;
    const view = host.current;
    if (!view || !picked) return undefined;
    openEditor(view, file, picked).then((close) => {
      if (gone) return close();
      stop = close;
      return picked.line && !picked.change
        ? reveal(view, picked.line)
        : undefined;
    }, said);
    return () => {
      gone = true;
      if (stop) stop();
    };
  }, [file]);

  /** @param {SubmitEvent & { currentTarget: HTMLFormElement }} event */
  const search = (event) => {
    event.preventDefault();
    const q = String(new FormData(event.currentTarget).get("q")).trim();
    if (!q) return setFound(null);
    return read.search(id, picked?.project || "", q).then(setFound, said);
  };

  const changed = mode === "changes";
  return html`<section
    class="code"
    role="tabpanel"
    id="panel-code"
    aria-labelledby="tab-code"
  >
    <form class="code-search" onSubmit=${search}>
      <input
        name="q"
        aria-label=${t("code.search")}
        placeholder=${t("code.search")}
        autocomplete="off"
      />
      <span class="code-modes"
        >${["files", "changes"].map(
          (one) =>
            html`<button
              type="button"
              onClick=${() => {
                setMode(one);
                setFound(null);
              }}
              aria-pressed=${String(mode === one)}
            >
              ${t(`code.${one}`)}
            </button>`,
        )}</span
      >
    </form>
    ${failure ? html`<p class="detail problem">${failure}</p>` : ""}
    <div class="code-body">
      <div class="code-names">
        <${Names}
          found=${found}
          changed=${changed}
          changes=${changes}
          trees=${trees}
          picked=${picked}
          failure=${failure}
          pick=${setPicked}
        />
        ${found?.cut ? html`<p class="muted">${t("code.cut")}</p>` : ""}
      </div>
      <div class="code-file">
        ${file && file.kind !== "TEXT" ? html`<p class="muted">${t(SAID[file.kind])}</p>` : ""}
        <div class="code-view" ref=${host}></div>
      </div>
    </div>
  </section>`;
};
