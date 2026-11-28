import { html } from "../components/html.js";
import { Pane } from "../components/Pane.js";
import { cards } from "./cards.js";
import { State } from "./State.js";

const PLAN = {
  names: ["plan.md", "notes.md"],
  name: "plan.md",
  html: '<p>Retry twice, then stop.</p><div class="mermaid">graph LR; retry --&gt; stop</div>',
};

const DIFF = [
  {
    project: "alpha",
    base: "origin/main",
    diff: "@@ -1,2 +1,2 @@\n-const retries = 3;\n+const retries = 2;\n export { retries };",
  },
];

export const readingSection = html`<section aria-label="Reading">
  <h2>Reading</h2>
  <main>
    <${State} name="the open task, its documents: two to choose from, a diagram not drawn yet"><${Pane}
      card=${cards[1].props} tab="artifacts" shown=${PLAN}/></${State}>
    <${State} name="the open task, its diff: a line removed, a line added"><${Pane}
      card=${cards[1].props} tab="diff" shown=${DIFF}/></${State}>
    <${State} name="the open task, the agent's last screen line under the overview"><${Pane}
      card=${cards[1].props} tab="overview" shown=${null} peek=${{ held: true, line: "› npm test" }}/></${State}>
  </main>
</section>`;
