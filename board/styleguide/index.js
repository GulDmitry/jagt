import { render } from "preact";
import { html } from "../components/html.js";
import "../ui/tips.js";
import { sections } from "./book.js";

const host = document.getElementById("styleguide");
if (host)
  render(
    ["light", "dark"].map(
      (theme) =>
        html`<div class="theme" data-theme=${theme} aria-label=${theme}>
          ${sections}
        </div>`,
    ),
    host,
  );
