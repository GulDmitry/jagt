import { render } from "preact";
import { html } from "../components/html.js";
import { Report } from "../components/Report.js";
import { reportParts } from "../core/format.js";
import * as store from "../core/store.js";

const report = document.getElementById("report");
const reportTitle = document.getElementById("report-title");
const reportBody = document.getElementById("report-body");
const reportScroll = document.getElementById("report-scroll");
const reportSection = document.getElementById("report-section");
const reportLine = document.getElementById("report-line");
const sayInput = document.getElementById("say");

export const isOpen = () => report.open;

export const onClose = (listener) => report.addEventListener("close", listener);

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

export function redraw(said) {
  const where = reportScroll.scrollTop;
  drawn(said);
  reportScroll.scrollTop = where;
}

// Which element the press STARTED on decides: selecting text inside and releasing outside asks for nothing.
function closeOnBackdrop(dialog) {
  let pressedBackdrop = false;
  dialog.addEventListener("mousedown", (event) => {
    pressedBackdrop = event.target === dialog;
  });
  dialog.addEventListener("click", (event) => {
    if (pressedBackdrop && event.target === dialog) dialog.close();
  });
}

document.getElementById("close-report").onclick = () => report.close();
closeOnBackdrop(report);
