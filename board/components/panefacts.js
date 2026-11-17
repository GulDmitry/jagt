/**
 * @typedef {import("./Pane.js").PaneCard} PaneCard
 * @typedef {import("./Pane.js").Window} Window
 * @typedef {import("./Pane.js").Peek} Peek
 */

/**
 * @param {string} tab
 * @param {PaneCard} card
 * @param {Window | null} shown
 * @param {Peek} peek
 */
export const dockOf = (tab, card, shown, peek) => {
  const session = tab === "session";
  const named = session && shown ? shown.name : null;
  const held = session
    ? shown
      ? Boolean(shown.name) || shown.held
      : null
    : peek.held;
  return {
    named,
    off: held === false,
    canRun:
      !named && (card.task.actions || []).some((action) => action.id === "run"),
    line: !session && card.task.owner === "AGENT" && peek.held ? peek.line : "",
  };
};
