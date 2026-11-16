import { connected } from "./connection.js";
import { repaintReport } from "./dialogs.js";
import { loadVerbs, readBoard } from "./load.js";
import * as tabs from "./tabs.js";

// A new run reloads the page, but not under a form someone is filling in.
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

export function listen() {
  const events = new EventSource("/api/events");
  let silence = 0;
  // Every connect reads the board, the first one included: whatever changed while disconnected sent no event.
  events.addEventListener("open", (event) => {
    // The server's first message is also named `open`; one connect is one read. It names the run: a page that
    // outlived a restart loads the new board.
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
  events.addEventListener("beat", (beat) => {
    clearTimeout(silence);
    silence = setTimeout(
      () => {
        events.close();
        connected(false);
        retry();
      },
      Number(beat.data) * 2.5,
    );
  });
  // An open report is read again on the same signal: the round it shows may be the thing that changed.
  events.addEventListener("changed", () => {
    tabs.forget();
    readBoard();
    repaintReport();
  });
  // A refused connect is final for an EventSource; only a new one tries again.
  events.onerror = () => {
    clearTimeout(silence);
    connected(false);
    if (events.readyState === EventSource.CLOSED) retry();
  };
}
