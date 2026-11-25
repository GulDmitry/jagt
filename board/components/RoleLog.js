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
    <button class="back" data-back="">${t("rolelog.back", { role })}</button>
    <div class="log-scroll">
      <${LogLines}
        said=${lines.join("\n")}
        empty=${t("rolelog.empty", { role })}
      />
    </div>
  </section>`;
