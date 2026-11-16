import { bothNames } from "./format.js";

/**
 * @typedef {{ id: string, alias?: string }} Task
 * @typedef {{ id: string, part?: string, aliases?: string[], takesTask?: boolean, takesProject?: boolean, hint?: string }} Verb
 */

/**
 * @type {{ tasks: Task[], verbs: Verb[], offers: Record<string, string[]> } & Record<string, any>}
 */
const data = {
  tasks: [],
  projects: [],
  autoReview: { summary: "", enabled: false },
  jobs: null,
  actStop: null,
  assistant: null,
  reach: [],
  masterMode: "off",
  verbs: [],
  branchStrategies: [],
  phases: [],
  offers: {},
};

export const tasks = () => data.tasks;
export const projects = () => data.projects;
export const branchStrategies = () => data.branchStrategies;
export const phases = () => data.phases;
export const autoReview = () => data.autoReview;
export const jobs = () => data.jobs;
export const actStop = () => data.actStop;
export const reach = () => data.reach;
export const masterMode = () => data.masterMode;
export const verbs = () => data.verbs;
/** @param {Task} task */
export const offersOn = (task) => data.offers[task.id] || [];

// The verb a task's card offers for a tab: the server's verb says which tab it feeds, by its part.
/**
 * @param {Task} task
 * @param {string} part
 */
export const offerFor = (task, part) =>
  data.verbs.find(
    (verb) => verb.part === part && offersOn(task).includes(verb.id),
  );

/** @param {string} name */
export const taskFor = (name) =>
  data.tasks.find((task) => task.id === name || (task.alias || "") === name);

// An empty reference names no task: the loose lookup above would hand it the first task without an alias.
/** @param {string} ref */
export const nameOf = (ref) => {
  const task = ref ? taskFor(ref) : null;
  return task ? bothNames(task) : ref;
};

/** @param {string} id */
export const taskById = (id) => data.tasks.find((task) => task.id === id);

/** @param {Record<string, any>} next */
export function set(next) {
  Object.assign(data, next);
}
