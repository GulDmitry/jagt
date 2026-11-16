import { make } from "../core/dom.js";
import { toast } from "./toast.js";

const state = {
  loading: null,
};

const load = () => {
  state.loading ??= new Promise((resolve, reject) => {
    const script = document.createElement("script");
    script.src = "./vendor/mermaid.min.js";
    script.onload = () => {
      globalThis.mermaid.initialize({
        startOnLoad: false,
        securityLevel: "strict",
      });
      resolve(globalThis.mermaid);
    };
    script.onerror = () => {
      state.loading = null;
      reject(new Error("mermaid did not load"));
    };
    document.head.append(script);
  });
  return state.loading;
};

export function draw(root) {
  const fences = root
    ? [...root.querySelectorAll("pre > code.language-mermaid")]
    : [];
  if (!fences.length) return;
  const nodes = fences.map((code) => {
    const node = make("div", "mermaid", code.textContent);
    code.parentElement.replaceWith(node);
    return node;
  });
  load()
    .then((mermaid) => mermaid.run({ nodes }))
    .catch((e) => toast(`could not draw the diagram: ${e.message}`, true));
}
