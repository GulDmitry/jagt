import { t } from "../core/i18n.js";
import { marked, watchLine } from "./facts.js";

/**
 * @typedef {import("../core/store.js").Task} CardTask
 * @typedef {import("../core/store.js").Action} Action
 * @typedef {{ name: string, kind: string, tasks: CardTask[], named?: boolean }} Group
 */

/** @param {Action} action */
export const quickClass = (action) =>
  action.primary ? "row-verb primary" : "row-verb";

/**
 * @param {CardTask} task
 * @param {number} now
 */
export const stoppedLine = (task, now) => {
  const watch = watchLine(task.autoReview, now);
  return watch?.stalled ? watch.line : null;
};

/**
 * @param {CardTask} task
 * @param {boolean} selected
 */
export const rowClass = (task, selected) =>
  [
    "row",
    task.owner.toLowerCase(),
    task.attention === "OPTIONAL" && "optional",
    selected && "selected",
    task.flagged && "flagged",
  ]
    .filter(Boolean)
    .join(" ");

/**
 * @param {CardTask} task
 * @param {boolean} changed
 * @param {string | null} stopped
 */
export const pickFacts = (task, changed, stopped) =>
  [
    marked(task) &&
      `checks: ${task.pipelineSaid || task.pipeline.toLowerCase()}`,
    stopped,
    changed && t("list.changed"),
  ]
    .filter(Boolean)
    .join(", ");

/** @param {CardTask[]} tasks */
export const manyProjects = (tasks) =>
  new Set(
    tasks.flatMap((task) => (task.repos || []).map((repo) => repo.project)),
  ).size > 1;

/**
 * @param {CardTask[]} shown
 * @param {boolean} many
 * @param {(task: CardTask) => boolean} [flagged]
 * @returns {Group[]}
 */
export function grouped(shown, many, flagged = () => false) {
  const leading = shown.filter(flagged);
  const ordered = [
    ...leading.map((task) => ({ ...task, flagged: true })),
    ...shown.filter((task) => !leading.includes(task)),
  ];
  const yours = ordered.filter((task) => task.attention === "REQUIRED");
  const rest = ordered.filter((task) => task.attention !== "REQUIRED");
  const projects = [...new Set(rest.map((task) => task.project))].sort((a, b) =>
    a.localeCompare(b, "en"),
  );
  const groups = many
    ? projects.map((project) => ({
        name: project,
        kind: "project",
        tasks: rest.filter((task) => task.project === project),
      }))
    : [
        {
          name: yours.length ? t("list.rest") : "",
          kind: "project",
          tasks: rest,
        },
      ];
  return [
    ...(yours.length
      ? [{ name: t("list.yours"), kind: "yours", tasks: yours, named: many }]
      : []),
    ...groups,
  ].filter((group) => group.tasks.length);
}
