import { t } from "../core/i18n.js";

/** @param {import("../core/store.js").Action | undefined} rowVerb */
export const said = (rowVerb) =>
  [
    t("keys.walk"),
    t("keys.open"),
    t("keys.next"),
    rowVerb?.key && `${rowVerb.key} ${rowVerb.label.toLowerCase()}`,
    t("keys.new"),
    t("keys.filter"),
    t("keys.command"),
  ]
    .filter(Boolean)
    .join(" · ");
