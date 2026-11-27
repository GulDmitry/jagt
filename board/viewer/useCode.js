import { useEffect, useRef, useState } from "preact/hooks";
import { open as openEditor, reveal } from "./editor.js";
import * as read from "./read.js";

/**
 * @typedef {import("./read.js").Tree} Tree
 * @typedef {import("./read.js").Repo} Repo
 * @typedef {import("./read.js").Content} Content
 * @typedef {import("./read.js").Found} Found
 * @typedef {import("./read.js").Picked} Picked
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

/** @param {{ id: string }} task */
export const useCode = (task) => {
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

  return {
    trees,
    picked,
    setPicked,
    file,
    found,
    setFound,
    mode,
    setMode,
    changes,
    failure,
    host,
    search,
  };
};
