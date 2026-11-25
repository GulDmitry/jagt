import { t } from "../core/i18n.js";
import { LogLines } from "./LogLines.js";
import { html } from "./html.js";

/** @param {{ role: string, lines: string[] }} props */
export const RoleLog = ({ role, lines }) =>
  html`<section
    id="pane"
    class="role-log"
    aria-label=${t("rolelog.label", { role })}
  >
    <button class="back" data-back="">${t("pane.back")}</button>
    <div class="log-scroll">
      ${
        lines.length
          ? html`<${LogLines} said=${lines.join("\n")} />`
          : t("rolelog.empty", { role })
      }
    </div>
  </section>`;
