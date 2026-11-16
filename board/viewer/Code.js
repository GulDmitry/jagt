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

const firstOf = (trees) => {
  const tree = trees.find((each) => each.files.length);
  if (!tree) return null;
  return {
    project: tree.project,
    path: tree.files.find((name) => /^readme/i.test(name)) || tree.files[0],
    line: 0,
  };
};

const SAID = { BINARY: "code.binary", TOO_LARGE: "code.large" };

export const Code = ({ task }) => {
  const [trees, setTrees] = useState(null);
  const [picked, setPicked] = useState(null);
  const [file, setFile] = useState(null);
  const [found, setFound] = useState(null);
  const [mode, setMode] = useState("files");
  const [changes, setChanges] = useState(null);
  const [failure, setFailure] = useState("");
  const host = useRef(null);
  const id = task.id;
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
    let stop = null;
    openEditor(host.current, file, picked).then((close) => {
      if (gone) return close();
      stop = close;
      return picked.line && !picked.change
        ? reveal(host.current, picked.line)
        : undefined;
    }, said);
    return () => {
      gone = true;
      if (stop) stop();
    };
  }, [file]);

  const search = (event) => {
    event.preventDefault();
    const q = event.target.elements.q.value.trim();
    if (!q) return setFound(null);
    return read.search(id, picked?.project || "", q).then(setFound, said);
  };

  const project = picked?.project || "";
  const changed = mode === "changes";
  const listed = changed ? changes || [] : [];
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
        ${
          found
            ? found.hits.length
              ? found.hits.map(
                  (hit) =>
                    html`<button
                      onClick=${() => setPicked({ project, path: hit.path, line: hit.line })}
                      aria-pressed=${String(picked?.path === hit.path && picked?.line === hit.line)}
                    >
                      ${hit.path}:${hit.line}
                      <span class="muted">${hit.text.trim()}</span>
                    </button>`,
                )
              : html`<p class="muted">${t("code.no-hits")}</p>`
            : changed
              ? changes === null
                ? html`<p class="muted">${t("reading")}</p>`
                : !listed.some((repo) => repo.files.length)
                  ? html`<p class="muted">${t("pane.no-diff")}</p>`
                  : listed.flatMap((repo) => [
                      ...repo.files.map(
                        (one) =>
                          html`<button
                            class="code-change"
                            onClick=${() => setPicked({ project: repo.project, path: one.path, line: 0, change: one })}
                            aria-pressed=${String(picked?.project === repo.project && picked?.path === one.path && !!picked.change)}
                          >
                            ${one.path}
                            <span class="muted"
                              >${` +${one.added} \u2212${one.removed}`}</span
                            >
                          </button>`,
                      ),
                      repo.cut
                        ? html`<p class="muted">${t("pane.diff-cut")}</p>`
                        : "",
                    ])
              : trees === null
                ? failure
                  ? ""
                  : html`<p class="muted">${t("reading")}</p>`
                : !picked
                  ? html`<p class="muted">${t("code.none")}</p>`
                  : trees.flatMap((tree) =>
                      tree.files.map(
                        (name) =>
                          html`<button
                            onClick=${() => setPicked({ project: tree.project, path: name, line: 0 })}
                            aria-pressed=${String(picked.project === tree.project && picked.path === name)}
                          >
                            ${name}
                          </button>`,
                      ),
                    )
        }
        ${found?.cut ? html`<p class="muted">${t("code.cut")}</p>` : ""}
      </div>
      <div class="code-file">
        ${file && file.kind !== "TEXT" ? html`<p class="muted">${t(SAID[file.kind])}</p>` : ""}
        <div class="code-view" ref=${host}></div>
      </div>
    </div>
  </section>`;
};
