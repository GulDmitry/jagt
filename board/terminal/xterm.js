/**
 * @typedef {{ cols: number, rows: number, open: (el: Element) => void, loadAddon: (addon: object) => void, write: (bytes: Uint8Array) => void, onData: (f: (data: string) => void) => void, onResize: (f: (size: { cols: number, rows: number }) => void) => void, dispose: () => void }} Xterm
 * @typedef {{ fit: () => void }} Fit
 */

const url = (/** @type {string} */ file) =>
  new URL(`../vendor/xterm/${file}`, import.meta.url).href;

/** The vendored files, which no type check follows. */
export const load = async () => {
  const [{ Terminal }, { FitAddon }] = await Promise.all([
    import(url("xterm.mjs")),
    import(url("addon-fit.mjs")),
  ]);
  return {
    terminal: () => /** @type {Xterm} */ (new Terminal({ cursorBlink: true })),
    fit: () => /** @type {Fit & object} */ (new FitAddon()),
  };
};
