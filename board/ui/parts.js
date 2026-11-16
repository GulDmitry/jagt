import * as addProject from "./addproject.js";
import * as jobs from "./jobs.js";
import * as launch from "./launch.js";
import * as legend from "./legend.js";
import * as palette from "./palette.js";

document.getElementById("new-task").onclick = launch.open;
document.getElementById("empty-new").onclick = launch.open;
document.getElementById("add-project").onclick = addProject.open;
document.getElementById("empty-project").onclick = addProject.open;

export const parts = {
  launch: { focus: launch.open },
  project: { focus: addProject.open, argued: addProject.argued },
  legend: { section: legend.node },
  jobs: { section: jobs.node },
};

palette.wire({ partFor: (part) => parts[part] || {} });
