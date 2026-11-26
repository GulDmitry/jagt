import { closest, make, span } from "../core/dom.js";
import * as store from "../core/store.js";
import * as filters from "./filters.js";
import * as order from "./order.js";
import { clearLabel, emptyFacts, orderFacts, segments } from "./phasefacts.js";

const phaseBar = /** @type {HTMLElement} */ (document.getElementById("phases"));

/**
 * @param {import("../core/store.js").Task[]} tasks
 * @param {number} showing
 * @param {boolean} logStands
 */
export function render(tasks, showing, logStands) {
  const empty = emptyFacts(tasks.length, showing, filters.on(), logStands);
  /** @type {HTMLElement} */ (document.getElementById("empty")).hidden =
    empty.emptyHidden;
  const filtered = /** @type {HTMLElement} */ (
    document.getElementById("filtered")
  );
  filtered.hidden = empty.filteredHidden;
  if (!filtered.hidden) filtered.textContent = empty.filtered;
  phaseBar.hidden = tasks.length === 0 || (logStands && showing === 0);
  phaseBar.replaceChildren(
    ...segments(store.phases(), filters.narrowed(tasks), filters.holds).flatMap(
      (one, index) => {
        const segment = make(
          "button",
          one.className,
          `${one.label} `,
          span("count", String(one.held)),
        );
        segment.disabled = one.held === 0;
        segment.dataset.phase = one.phase;
        segment.setAttribute("aria-pressed", String(one.pressed));
        segment.dataset.tip = one.tip;
        return index === 0 ? [segment] : [span("sep", " · "), segment];
      },
    ),
  );
  const facts = orderFacts(order.holds());
  const sort = make("button", "order", facts.label);
  sort.dataset.order = "";
  sort.dataset.tip = facts.tip;
  phaseBar.append(span("sep", " · "), sort);
  if (filters.on()) {
    const clear = make("button", "clear-filters", clearLabel(filters.on()));
    clear.dataset.clear = "";
    phaseBar.append(clear);
  }
}

/** @param {() => void} repaint */
export const onBarClick = (repaint) => {
  phaseBar.onclick = (event) => {
    const button = closest(event, "button");
    if (!button) return;
    if (button.dataset.phase) filters.togglePhase(button.dataset.phase);
    else if (button.hasAttribute("data-order")) order.toggle();
    else if (button.hasAttribute("data-clear")) filters.clear();
    else return;
    repaint();
  };
};
