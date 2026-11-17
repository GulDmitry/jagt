import { useEffect, useRef, useState } from "preact/hooks";
import { t } from "../core/i18n.js";
import { html } from "../components/html.js";
import { load } from "./xterm.js";
import { NO_WINDOW, resized, streamUrl, typed } from "./stream.js";

document.head.append(
  ...["../vendor/xterm/xterm.css", "./terminal.css"].map((href) =>
    Object.assign(document.createElement("link"), {
      rel: "stylesheet",
      href: new URL(href, import.meta.url).href,
    }),
  ),
);

/**
 * @param {Element} mount
 * @param {{ task: string, name: string | null | undefined }} target
 * @param {() => void} gone
 */
const attach = async (mount, target, gone) => {
  const { terminal, fit } = await load();
  const term = terminal();
  const sizer = fit();
  term.loadAddon(sizer);
  term.open(mount);
  sizer.fit();
  const socket = new WebSocket(
    streamUrl(location, target, { cols: term.cols, rows: term.rows }),
  );
  socket.binaryType = "arraybuffer";
  socket.onmessage = (event) => term.write(new Uint8Array(event.data));
  socket.onclose = (event) => {
    if (event.code === NO_WINDOW) gone();
  };
  const send = (/** @type {string} */ frame) => {
    if (socket.readyState === WebSocket.OPEN) socket.send(frame);
  };
  term.onData((data) => send(typed(data)));
  term.onResize((size) => send(resized(size)));
  const observer = new ResizeObserver(() => sizer.fit());
  observer.observe(mount);
  return () => {
    observer.disconnect();
    socket.onclose = null;
    socket.close();
    term.dispose();
  };
};

/** @param {{ task: string, name: string | null | undefined }} props */
export const Terminal = ({ task, name }) => {
  const host = useRef(/** @type {HTMLDivElement | null} */ (null));
  const [gone, setGone] = useState(false);
  useEffect(() => {
    const mount = host.current;
    if (!mount) return undefined;
    setGone(false);
    const attached = attach(mount, { task, name }, () => setGone(true));
    return () => {
      attached.then((detach) => detach());
    };
  }, [task, name]);
  return gone
    ? html`<p class="muted">${t("pane.no-window")}</p>`
    : html`<div class="terminal-host" ref=${host}></div>`;
};
