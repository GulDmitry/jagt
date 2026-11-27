import { api, cause } from "../core/api.js";
import { t } from "../core/i18n.js";

/** @param {string | null} windowName */
export async function sessionOf(windowName) {
  const names = await api("/api/windows").catch(() => []);
  const name = names.includes(windowName) ? windowName : null;
  try {
    const { Terminal } = await import("./Terminal.js");
    return { held: true, Terminal, names, name };
  } catch (e) {
    return {
      held: true,
      shown: t("tabs.unread", { cause: cause(e) }),
      names,
      name,
    };
  }
}
