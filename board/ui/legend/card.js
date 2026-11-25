import { make, span } from "../../core/dom.js";
import { t } from "../../core/i18n.js";

/**
 * @param {string} key
 * @param {Node[]} samples
 */
export const row = (key, ...samples) => ({ key, samples });

/** @param {string} owner */
const edge = (owner) => span(`legend-edge ${owner}`, "");

/**
 * @param {string} className
 * @param {string} text
 * @param {string} age
 */
const aged = (className, text, age) =>
  make("span", className, text, " ", span("age", age));

/**
 * @param {string} className
 * @param {string} text
 */
const requestChip = (className, text) => make("a", className, text);

const repositories = () =>
  make(
    "span",
    "repos",
    "",
    requestChip("mr-age", "alpha"),
    " + ",
    requestChip("mr-age", "beta"),
    " ",
    span("tick", "✓"),
  );

/** @param {boolean} on */
const flagButton = (on) => {
  const button = make("button", "flag", on ? "⚑" : "⚐");
  button.setAttribute("aria-pressed", String(on));
  return button;
};

const draftedTab = () => {
  const tab = make("button", "you", t("tab.round"));
  tab.setAttribute("role", "tab");
  return make("span", "tabs", "", tab);
};

const blockHead = () =>
  make("span", "block", "1 · ", span("verdict ok", "FIXED"), " · thread 4");

/**
 * @param {string} className
 * @param {string} text
 */
const detailLine = (className, text) => make("div", className, text);

export const cardRows = () => [
  row("edges", edge("you"), edge("agent"), edge("ci"), edge("")),
  row(
    "badges",
    span("badge required", "ship it"),
    span("badge optional", "read the round"),
    span("badge required problem", "NEEDS YOU"),
  ),
  row("flag", flagButton(false), flagButton(true)),
  row(
    "status",
    aged("status", "in progress", "12m"),
    aged("status live", "done", "3d"),
  ),
  row("deployed", make("button", "again", "Deploy")),
  row("quiet", make("button", "quiet", "Check review")),
  row(
    "request",
    requestChip("id", "ABC-42"),
    requestChip("mr-age approved", `${t("request")} 5d ✓`),
    requestChip("mr-age", `${t("request")} 2d`),
  ),
  row(
    "checks",
    ...["red", "green", "running"].map((s) => span(`checks ${s}`, "")),
  ),
  row("repos", requestChip("mr-age", "alpha 2d"), repositories()),
  row("drafted", draftedTab()),
  row("verdict", blockHead(), span("quote", "> the row count is wrong")),
  row(
    "stalled",
    requestChip("mr-age stalled", `${t("request")} 9d`),
    aged("status stalled", "out for review", "9d"),
    ...["act-stopped", "board-stopped", "start-interrupted"].map((k) =>
      span("chip stalled", t(k)),
    ),
  ),
  row(
    "detail",
    detailLine("detail problem", "PROBLEM: no branch to ship"),
    detailLine("detail you", "NEEDS YOU: a red run"),
  ),
];
