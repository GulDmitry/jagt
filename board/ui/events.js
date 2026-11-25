import { connected } from "./connection.js";
import { repaintReport } from "./dialogs.js";
import { loadVerbs, readBoard } from "./load.js";
import * as rolelog from "./rolelog.js";
import * as tabs from "./tabs.js";

function reloadOnceNoDialogIsOpen() {
  const open = document.querySelector("dialog[open]");
  if (open)
    open.addEventListener("close", reloadOnceNoDialogIsOpen, { once: true });
  else window.location.reload();
}

const state = {
  retryIn: 1000,
  servedBy: null,
};

function retry() {
  setTimeout(listen, state.retryIn);
  state.retryIn = Math.min(state.retryIn * 2, 30000);
}

// The server's beat interval until its first beat says it: a connect that never answers is retried too.
const FIRST_BEAT = 20000;

export function listen() {
  const events = new EventSource("/api/events");
  let silence = 0;
  const watch = (/** @type {number} */ interval) => {
    clearTimeout(silence);
    silence = setTimeout(() => {
      events.close();
      connected(false);
      retry();
    }, interval * 2.5);
  };
  watch(FIRST_BEAT);
  events.addEventListener("open", (event) => {
    if (event instanceof MessageEvent) {
      if (state.servedBy && state.servedBy !== event.data)
        reloadOnceNoDialogIsOpen();
      state.servedBy = event.data;
      return;
    }
    state.retryIn = 1000;
    connected(true);
    loadVerbs();
    readBoard();
  });
  // A connection a sleeping laptop dropped stays open to the page: two missed beats are what says it is gone.
  events.addEventListener("beat", (beat) => watch(Number(beat.data)));
  events.addEventListener("changed", () => {
    tabs.forget();
    readBoard();
    repaintReport();
  });
  // A role's log gained a line: the open one reads what is new.
  events.addEventListener("role-log", (line) => {
    if (line instanceof MessageEvent) rolelog.follow(line.data);
  });
  // A refused connect is final for an EventSource; only a new one tries again.
  events.onerror = () => {
    connected(false);
    if (events.readyState === EventSource.CLOSED) {
      clearTimeout(silence);
      retry();
    } else watch(FIRST_BEAT);
  };
}
