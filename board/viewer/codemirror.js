let loading = null;

const GRAMMARS = {
  java: ["@codemirror/lang-java", "java"],
  js: ["@codemirror/lang-javascript", "javascript"],
  mjs: ["@codemirror/lang-javascript", "javascript"],
  json: ["@codemirror/lang-json", "json"],
  yml: ["@codemirror/lang-yaml", "yaml"],
  yaml: ["@codemirror/lang-yaml", "yaml"],
  md: ["@codemirror/lang-markdown", "markdown"],
  css: ["@codemirror/lang-css", "css"],
  html: ["@codemirror/lang-html", "html"],
  py: ["@codemirror/lang-python", "python"],
};

const readOnly = ({ view, state, language, highlight, search }, grammar) => [
  view.lineNumbers(),
  state.EditorState.readOnly.of(true),
  search.search({ top: true }),
  view.keymap.of(search.searchKeymap),
  language.syntaxHighlighting(highlight.classHighlighter),
  ...grammar,
];

const load = () => {
  loading ??= (async () => {
    const map = document.createElement("script");
    map.type = "importmap";
    map.textContent = await (
      await fetch("./vendor/codemirror/importmap.json")
    ).text();
    document.head.append(map);
    const [view, state, language, highlight, search, merge] = await Promise.all(
      [
        "@codemirror/view",
        "@codemirror/state",
        "@codemirror/language",
        "@lezer/highlight",
        "@codemirror/search",
        "@codemirror/merge",
      ].map((name) => import(name)),
    );
    return { view, state, language, highlight, search, merge };
  })().catch((e) => {
    loading = null;
    throw e;
  });
  return loading;
};

const grammarOf = async (path) => {
  const known = GRAMMARS[path.split(".").pop().toLowerCase()];
  if (!known) return [];
  const module = await import(known[0]);
  return [module[known[1]]()];
};

// A read-only view of one file; the returned function removes it.
export async function show(parent, text, path) {
  const { view, state, language, highlight, search } = await load();
  const grammar = await grammarOf(path);
  const editor = new view.EditorView({
    parent,
    state: state.EditorState.create({
      doc: text,
      extensions: readOnly(
        { view, state, language, highlight, search },
        grammar,
      ),
    }),
  });
  return () => editor.destroy();
}

// The file before and after its change, side by side; the returned function removes it.
export async function showChange(parent, before, after, path) {
  const { view, state, language, highlight, search, merge } = await load();
  const extensions = readOnly(
    { view, state, language, highlight, search },
    await grammarOf(path),
  );
  const pair = new merge.MergeView({
    parent,
    a: { doc: before, extensions },
    b: { doc: after, extensions },
  });
  return () => pair.destroy();
}

export const reveal = async (parent, line) => {
  const { view } = await load();
  const editor = view.EditorView.findFromDOM(parent);
  if (!editor || line > editor.state.doc.lines) return;
  const at = editor.state.doc.line(line);
  editor.dispatch({
    selection: { anchor: at.from },
    effects: view.EditorView.scrollIntoView(at.from, { y: "center" }),
  });
};
