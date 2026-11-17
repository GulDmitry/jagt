import { t } from "../core/i18n.js";

/**
 * @param {number} held
 * @param {number} showing
 * @param {number} on
 */
export const emptyFacts = (held, showing, on) => ({
  emptyHidden: held > 0,
  filteredHidden: !(held > 0 && showing === 0),
  filtered: t("header.filtered", { on, held }),
});

/**
 * @param {{ id: string, label: string }[]} phases
 * @param {import("../core/store.js").Task[]} perPhase
 * @param {(phase: string) => boolean} holds
 */
export const segments = (phases, perPhase, holds) =>
  phases.map(({ id, label }) => {
    const held = perPhase.filter((task) => task.phase === id).length;
    const pressed = holds(id);
    return {
      phase: id,
      label,
      held,
      className: held ? "phase" : "phase empty",
      pressed,
      tip: t(pressed ? "header.phase.off" : "header.phase.on", { label }),
    };
  });

// Names the order the board is in; the hover, the one a press takes it to.
/** @param {boolean} byAlias */
export const orderFacts = (byAlias) => ({
  label: t(byAlias ? "header.order.alias" : "header.order.added"),
  tip: t(byAlias ? "header.order.added.tip" : "header.order.alias.tip"),
});

/** @param {number} n */
export const clearLabel = (n) => t("header.clear", { n });
