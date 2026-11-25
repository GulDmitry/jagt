import { make, span } from "../core/dom.js";
import { t } from "../core/i18n.js";
import { cardRows } from "./legend/card.js";
import { pageRows } from "./legend/page.js";

export function node() {
  return make(
    "div",
    "legend",
    "",
    span("legend-head", t("legend.head")),
    ...[...cardRows(), ...pageRows()].flatMap(({ key, samples }) => {
      const sample = Object.assign(make("div", "legend-key", "", ...samples), {
        inert: true,
      });
      return [sample, span("legend-says", t(`legend.row.${key}`))];
    }),
  );
}
