import { render } from "preact";
import "../ui/tips.js";
import { cardSection } from "./cardSection.js";
import { checksSection, jobsSection } from "./jobsSection.js";
import { listSection } from "./listSection.js";

const host = document.getElementById("styleguide");
if (host) render([cardSection, listSection, jobsSection, checksSection], host);
