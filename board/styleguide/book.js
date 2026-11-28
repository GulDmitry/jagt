import { cardSection } from "./cardSection.js";
import { checksSection, jobsSection } from "./jobsSection.js";
import { legendSection } from "./legendSection.js";
import { panes } from "./panes.js";
import { statusSection, stopSection } from "./stateSections.js";

export const sections = [
  cardSection,
  statusSection,
  stopSection,
  ...panes,
  jobsSection,
  checksSection,
  legendSection,
];
