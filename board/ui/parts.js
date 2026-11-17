import * as addProject from "./addproject.js";
import * as jobs from "./jobs.js";
import * as launch from "./launch.js";
import * as legend from "./legend.js";
import * as palette from "./palette.js";

/** @type {HTMLElement} */ (document.getElementById("new-task")).onclick =
  launch.open;
/** @type {HTMLElement} */ (document.getElementById("empty-new")).onclick =
  launch.open;
/** @type {HTMLElement} */ (document.getElementById("add-project")).onclick =
  addProject.open;
/** @type {HTMLElement} */ (document.getElementById("empty-project")).onclick =
  addProject.open;

/** @type {Record<string, import("./execute.js").Part>} */
export const parts = {
  launch: { focus: launch.open },
  project: { focus: addProject.open, argued: addProject.argued },
  legend: { section: legend.node },
  jobs: { section: jobs.node },
};

palette.wire({ partFor: (part = "") => parts[part] || {} });
