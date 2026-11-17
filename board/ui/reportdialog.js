import { render } from "preact";
import { html } from "../components/html.js";
import { Report } from "../components/Report.js";

import { reportParts } from "../core/format.js";
import * as store from "../core/store.js";

const report = /** @type {HTMLDialogElement} */ (
  document.getElementById("report")
);
const reportTitle = /** @type {HTMLElement} */ (
  document.getElementById("report-title")
);
const reportBody = /** @type {HTMLElement} */ (
  document.getElementById("report-body")
);
const reportScroll = /** @type {HTMLElement} */ (
  document.getElementById("report-scroll")
);
const reportSection = /** @type {HTMLElement} */ (
  document.getElementById("report-section")
);
const reportLine = /** @type {HTMLElement} */ (
  document.getElementById("report-line")
);
const sayInput = /** @type {HTMLInputElement} */ (
  document.getElementById("say")
);

export const isOpen = () => report.open;

/** @param {() => void} listener */
export const onClose = (listener) => report.addEventListener("close", listener);

/** @param {string} said */
const drawn = (said) =>
  render(
    html`<${Report}
      parts=${reportParts(
        said,
        store.tasks().map((task) => task.id),
      )}
    />`,
    reportBody,
  );

/**
 * @param {string} title
 * @param {string} said
 * @param {import("./dialogs.js").Showing} showing
 */
export function paint(title, said, { extra, about }) {
  reportTitle.textContent = title;
  drawn(said);
  reportSection.replaceChildren(...(extra ? [extra] : []));
  // Only a report about ONE task has a session to talk to, and a line typed at one round is not the next one's.
  reportLine.hidden = !about;
  sayInput.value = "";
  if (!report.open) report.showModal();
  reportScroll.scrollTop = 0;
}

/** @param {string} said */
export function redraw(said) {
  const where = reportScroll.scrollTop;
  drawn(said);
  reportScroll.scrollTop = where;
}

// Which element the press STARTED on decides: selecting text inside and releasing outside asks for nothing.
/** @param {HTMLDialogElement} dialog */
function closeOnBackdrop(dialog) {
  let pressedBackdrop = false;
  dialog.addEventListener("mousedown", (event) => {
    pressedBackdrop = event.target === dialog;
  });
  dialog.addEventListener("click", (event) => {
    if (pressedBackdrop && event.target === dialog) dialog.close();
  });
}

/** @type {HTMLElement} */ (document.getElementById("close-report")).onclick =
  () => report.close();
closeOnBackdrop(report);
