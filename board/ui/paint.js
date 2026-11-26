import { render } from "preact";
import { draw } from "./mermaid.js";

/**
 * @param {object | null} vnode
 * @param {HTMLElement} root
 */
export const paint = (vnode, root) => {
  render(vnode, root);
  draw(root);
};
