// Draws the mermaid fences of a rendered document; the library is fetched only when a document holds one.

import {toast} from './toast.js';

let loading = null;

const load = () => {
  loading ??= new Promise((resolve, reject) => {
    const script = document.createElement('script');
    script.src = './vendor/mermaid.min.js';
    script.onload = () => {
      globalThis.mermaid.initialize({startOnLoad: false, securityLevel: 'strict'});
      resolve(globalThis.mermaid);
    };
    script.onerror = () => {
      loading = null;
      reject(new Error('mermaid did not load'));
    };
    document.head.append(script);
  });
  return loading;
};

export function draw(root) {
  const fences = root ? [...root.querySelectorAll('pre > code.language-mermaid')] : [];
  if (!fences.length) return;
  const nodes = fences.map((code) => {
    const node = document.createElement('div');
    node.className = 'mermaid';
    node.textContent = code.textContent;
    code.parentElement.replaceWith(node);
    return node;
  });
  load().then((mermaid) => mermaid.run({nodes})).catch((e) => toast(`could not draw the diagram: ${e.message}`, true));
}
