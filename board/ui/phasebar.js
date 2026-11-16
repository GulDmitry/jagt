import { make, span } from "../core/dom.js";
import { t } from "../core/i18n.js";
import * as store from "../core/store.js";
import * as filters from "./filters.js";
import * as order from "./order.js";

const phaseBar = document.getElementById("phases");

// An empty board is nothing created yet or everything filtered out: two elements, so neither shows the other's text.
function renderEmpty(held, showing) {
  document.getElementById("empty").hidden = held > 0;
  const filtered = document.getElementById("filtered");
  filtered.hidden = !(held > 0 && showing === 0);
  if (!filtered.hidden) {
    filtered.textContent = t("header.filtered", { on: filters.on(), held });
  }
}

export function render(tasks, showing) {
  renderEmpty(tasks.length, showing);
  phaseBar.hidden = tasks.length === 0;
  const perPhase = filters.narrowed(tasks);
  // The separator is content, not a gap: the words stay apart without the stylesheet.
  phaseBar.replaceChildren(
    ...store.phases().flatMap(({ id: phase, label }, index) => {
      const held = perPhase.filter((task) => task.phase === phase).length;
      const segment = make(
        "button",
        held ? "phase" : "phase empty",
        `${label} `,
        span("count", held),
      );
      segment.disabled = held === 0;
      segment.dataset.phase = phase;
      segment.setAttribute("aria-pressed", String(filters.holds(phase)));
      segment.dataset.tip = filters.holds(phase)
        ? t("header.phase.off", { label })
        : t("header.phase.on", { label });
      return index === 0 ? [segment] : [span("sep", " · "), segment];
    }),
  );
  // Names the order the board is in; the hover, the one a press takes it to.
  const sort = make(
    "button",
    "order",
    t(order.holds() ? "header.order.alias" : "header.order.added"),
  );
  sort.dataset.order = "";
  sort.dataset.tip = order.holds()
    ? t("header.order.added.tip")
    : t("header.order.alias.tip");
  phaseBar.append(span("sep", " · "), sort);
  if (filters.on()) {
    const clear = make(
      "button",
      "clear-filters",
      t("header.clear", { n: filters.on() }),
    );
    clear.dataset.clear = "";
    phaseBar.append(clear);
  }
}

export const onBarClick = (repaint) => {
  phaseBar.onclick = (event) => {
    const button = event.target.closest("button");
    if (!button) return;
    if (button.dataset.phase) filters.togglePhase(button.dataset.phase);
    else if (button.hasAttribute("data-order")) order.toggle();
    else if (button.hasAttribute("data-clear")) filters.clear();
    else return;
    repaint();
  };
};
