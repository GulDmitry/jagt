import { closest } from "../core/dom.js";
import * as filters from "./filters.js";
import { render } from "./render.js";
import * as selection from "./selection.js";

/** @type {HTMLElement} */ (
  document.getElementById("report-body")
).addEventListener("click", (event) => {
  const id = closest(event, "a[data-task]")?.dataset.task;
  if (!id) return;
  event.preventDefault();
  /** @type {HTMLDialogElement} */ (document.getElementById("report")).close();
  filters.box.value = id;
  selection.choose(id);
  render();
});

// A linked task lands in the FILTER, not a selection: that control is visible and clearing it is on the page.
const deepLink = new URLSearchParams(window.location.search).get("task");
if (deepLink) {
  filters.box.value = deepLink;
  selection.link(deepLink);
}
