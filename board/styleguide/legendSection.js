import { html } from "../components/html.js";
import { RoleLog } from "../components/RoleLog.js";
import { node } from "../ui/legend.js";
import { State } from "./State.js";

export const legendSection = html`<section aria-label="Legend">
  <h2>Legend</h2>
  <main>
    <${State} name="the Help report's legend: one row per mark">
      <div ref=${(/** @type {HTMLElement | null} */ host) => host?.replaceChildren(node())}></div>
    </${State}>
  </main>
</section>`;

export const logSection = html`<section aria-label="Role log">
  <h2>Role log</h2>
  <main>
    <${State} name="a role's log: newest first, a WARN in danger"><${RoleLog} role="reviewer" lines=${[
      "10:02:11 INFO review started task=ABC-42",
      "10:04:37 WARN request unreadable url=https://code.example/mr/1",
      "  retry at 10:05",
    ]}/></${State}>
    <${State} name="a role's log with nothing yet"><${RoleLog} role="reviewer" lines=${[]}/></${State}>
  </main>
</section>`;
