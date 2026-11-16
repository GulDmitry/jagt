import { render } from "preact";
import "../ui/tips.js";
import { cardSection } from "./cardSection.js";
import { checksSection, jobsSection } from "./jobsSection.js";
import { listSection } from "./listSection.js";

render(
  [cardSection, listSection, jobsSection, checksSection],
  document.getElementById("styleguide"),
);
