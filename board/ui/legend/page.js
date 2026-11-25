import { make, span } from "../../core/dom.js";
import { t } from "../../core/i18n.js";
import { choice } from "../choice.js";

const yoursHead = () =>
  make("div", "group yours", "", span("group-head", `${t("list.yours")} 2`));

/**
 * @param {string} className
 * @param {string} text
 * @param {string} [key]
 */
const verbButton = (className, text, key) => {
  const button = make("button", className, text);
  if (key) button.dataset.key = key;
  return button;
};

const failedLastRun = () =>
  make(
    "table",
    "jobs",
    "",
    make(
      "tbody",
      "",
      "",
      make(
        "tr",
        "",
        "",
        make("td", "last failed", t("jobs.ago", { when: "6m" })),
      ),
    ),
  );

/**
 * @param {string} className
 * @param {string} text
 */
const planChange = (className, text) =>
  make("div", "doc", "", span(className, text));

/** @param {string} className */
const toastEdge = (className) =>
  make(
    "div",
    className,
    t(className.includes("error") ? "sample.refused" : "sample.done"),
  );

/** @param {string} className */
const rowVerb = (className) => make("button", className, "▸_");

const gearButton = () => {
  const button = /** @type {HTMLElement} */ (
    document.getElementById("gear")?.cloneNode(true)
  );
  button.removeAttribute("id");
  return button;
};

const checkedChoice = () =>
  choice(
    "legend-theme",
    ["auto", "dark"].map((value) => ({ value, label: t(`theme.${value}`) })),
    "dark",
    () => {},
  );

export const pageRows = () => [
  [yoursHead()],
  [span("row-title changed", "Login form loses focus")],
  [
    planChange("changed", "Retry the upload twice"),
    planChange("removed", "Retry the upload once"),
  ],
  [rowVerb("row-verb primary"), rowVerb("row-verb")],
  [verbButton("", "Focus", "f"), verbButton("apart", "Done")],
  [failedLastRun()],
  [toastEdge("toast"), toastEdge("toast error")],
  [span(null, "(2) jagt")],
  [span("waiting", t("header.waiting", { n: 2 }))],
  [
    span("chip", t("auto-review-off")),
    span("chip bad", t("jobs-failed", { failing: 1, count: 16 })),
    span("chip", t("header.acting")),
    make("button", "chip log on", t("legend.role")),
  ],
  [
    span("chip bad", t("offline")),
    span("chip bad", t("assistant")),
    span("chip bad", "code host CLI not logged in"),
  ],
  [gearButton(), checkedChoice()],
];
