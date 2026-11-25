import { make } from "../core/dom.js";
import { t } from "../core/i18n.js";
import { withSeconds } from "../core/time.js";

const toasts = /** @type {HTMLElement} */ (document.getElementById("toasts"));
const opener = /** @type {HTMLElement} */ (document.getElementById("show-log"));
/** @type {string[]} */
const messages = [];

export const sessionLog = () => messages.join("\n");

/** @param {() => void} open */
export const showLog = (open) => {
  opener.onclick = open;
};

/**
 * @param {string} message
 * @param {boolean} [isError]
 */
export function toast(message, isError) {
  messages.push(
    `${withSeconds(Date.now())} ${isError ? "ERROR" : "INFO"} ${message}`,
  );
  opener.hidden = false;
  opener.dataset.tip = t("toast.log.tip", { message });

  const dismiss = Object.assign(
    make("button", isError ? "toast error" : "toast", message),
    { type: "button" },
  );
  const node = make("div", "", "", dismiss);
  if (isError) node.setAttribute("role", "alert");
  dismiss.onclick = () => node.remove();
  toasts.append(node);
  setTimeout(() => node.remove(), isError ? 12000 : 7000);
}
