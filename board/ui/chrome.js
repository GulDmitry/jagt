import * as header from "./header.js";
import * as launch from "./launch.js";
import { noted } from "./noted.js";
import * as projects from "./projects.js";
import { status } from "./status.js";

/** @param {import("./view.js").View} view */
export function chrome(view) {
  projects.render();
  launch.render();
  header.render(view.tasks, view.shown.length, view.logStands);
  noted(view);
  status(view.tasks);
}
