import { make } from "../core/dom.js";
import { t } from "../core/i18n.js";
import { toast } from "./toast.js";

/** @typedef {{ initialize: (config: object) => void, run: (options: { nodes: Element[] }) => Promise<void> }} Mermaid */

/** @type {{ loading: Promise<Mermaid> | null }} */
const state = {
  loading: null,
};

const vendored = () =>
  /** @type {{ mermaid: Mermaid }} */ (/** @type {unknown} */ (globalThis))
    .mermaid;

const load = () => {
  state.loading ??= new Promise((resolve, reject) => {
    const script = document.createElement("script");
    script.src = "./vendor/mermaid.min.js";
    script.onload = () => {
      vendored().initialize({
        startOnLoad: false,
        securityLevel: "strict",
      });
      resolve(vendored());
    };
    script.onerror = () => {
      state.loading = null;
      reject(new Error("mermaid did not load"));
    };
    document.head.append(script);
  });
  return state.loading;
};

/** @param {ParentNode} root */
export function draw(root) {
  const fences = [...root.querySelectorAll("pre > code.language-mermaid")];
  if (!fences.length) return;
  const nodes = fences.map((code) => {
    const node = make(
      "div",
      "mermaid",
      /** @type {string} */ (code.textContent),
    );
    code.parentElement?.replaceWith(node);
    return node;
  });
  load()
    .then((mermaid) => mermaid.run({ nodes }))
    .catch(
      /** @param {unknown} e */ (e) =>
        toast(
          t("mermaid.failed", {
            error: e instanceof Error ? e.message : String(e),
          }),
          true,
        ),
    );
}
