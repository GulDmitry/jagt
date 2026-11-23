import { t } from "../core/i18n.js";
import { html } from "./html.js";

/** @param {HTMLElement | null} node */
const following = (node) => {
  if (node) node.scrollTop = node.scrollHeight;
};

/** @param {{ role: string, lines: string[] }} props */
export const RoleLog = ({ role, lines }) =>
  html`<section
    id="pane"
    class="role-log"
    aria-label=${t("rolelog.label", { role })}
  >
    <button class="back" data-back="">${t("pane.back")}</button>
    <pre ref=${following}>
${lines.length ? lines.join("\n") : t("rolelog.empty", { role })}</pre>
  </section>`;
