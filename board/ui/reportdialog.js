import { render } from "preact";
import { html } from "../components/html.js";
import { ReportBody } from "../components/Report.js";
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

/**
 * @param {string} said
 * @param {boolean} log
 */
const drawn = (said, log) => {
  reportBody.classList.toggle("log", log);
  render(
    html`<${ReportBody}
      said=${said}
      taskIds=${store.tasks().map((task) => task.id)}
      log=${log}
    />`,
    reportBody,
  );
};

/**
 * @param {string} title
 * @param {string} said
 * @param {import("./dialogs.js").Showing} showing
 */
export function paint(title, said, { extra, about, log }) {
  reportTitle.textContent = title;
  drawn(said, log);
  reportSection.replaceChildren(...(extra ? [extra] : []));
  reportLine.hidden = !about;
  sayInput.value = "";
  if (!report.open) report.showModal();
  reportScroll.scrollTop = 0;
}

/**
 * @param {string} said
 * @param {boolean} log
 */
export function redraw(said, log) {
  const where = reportScroll.scrollTop;
  drawn(said, log);
  reportScroll.scrollTop = where;
}

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
