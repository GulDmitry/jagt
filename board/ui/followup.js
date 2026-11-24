import { sayTo } from "../core/api.js";

import { t } from "../core/i18n.js";
import { sending } from "./submit.js";
import { toast } from "./toast.js";

const reportLine = /** @type {HTMLFormElement} */ (
  document.getElementById("report-line")
);
const sayInput = /** @type {HTMLInputElement} */ (
  document.getElementById("say")
);
const working = /** @type {HTMLElement} */ (document.getElementById("said"));

const EVERY = 3000;
const GIVE_UP = 300000;

const state = {
  poll: 0,
};

export function stopWaiting() {
  working.hidden = true;
  if (state.poll) clearInterval(state.poll);
  state.poll = 0;
}

function giveUp() {
  stopWaiting();
  toast(t("report.silent"));
}

/** @param {() => void} repaint */
function waitForTheAnswer(repaint) {
  stopWaiting();
  working.hidden = false;
  const until = Date.now() + GIVE_UP;
  state.poll = setInterval(
    () => (Date.now() > until ? giveUp() : repaint()),
    EVERY,
  );
}

/**
 * The line typed at an open report goes to `about()`'s session, and the answer is followed with `repaint`.
 * @param {{ about: () => string | null, repaint: () => void }} handlers
 */
export const follow = ({ about, repaint }) => {
  reportLine.onsubmit = (event) => {
    event.preventDefault();
    const line = sayInput.value.trim();
    const task = about();
    if (!line || !task) {
      return;
    }
    sending(reportLine, {
      waiting: "",
      send: () => sayTo(task, line),
      done: () => {
        sayInput.value = "";
        waitForTheAnswer(repaint);
      },
    });
  };
};
