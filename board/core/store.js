import { bothNames } from "./format.js";

/**
 * @typedef {{ id: string, label: string, group?: string, hint?: string, key?: string, primary?: boolean, again?: boolean, apart?: boolean, onRow?: boolean, readOnly?: boolean }} Action
 * @typedef {{ status: string, at: number, label?: string, why?: string, origin?: string, role?: string }} Step
 * @typedef {{ project: string, reviewRequestUrl?: string }} TaskRepo
 * @typedef {{ state?: string, note?: string, label?: string, nextPollAt: number }} Watch
 * @typedef {{
 *   id: string, alias?: string, title?: string, project: string, repos?: TaskRepo[], phase?: string,
 *   confirmations?: Record<string, string>, repliesWrittenAt?: number,
 *   owner: string, attention: string, ask?: string, hint?: string, detail?: string, detailKind?: string,
 *   tier?: string, ticketUrl?: string, status?: string, statusLabel: string, statusSince: number,
 *   actions?: Action[], history?: Step[], switches?: Record<string, string>,
 *   stop?: { reason?: string, evidence?: string, next?: string, at: number, triage?: string },
 *   reviewRequestUrl?: string, requestOpenedAt?: number, approved?: boolean | null,
 *   pipeline: string, pipelineSaid?: string, pipelineUnread?: boolean,
 *   autoReview?: Watch | null, waiting?: string, deployed?: boolean
 * }} CardTask
 */

/**
 * @typedef {CardTask} Task
 * @typedef {{ id: string, part?: string, aliases?: string[], takesTask?: boolean, takesProject?: boolean, hint?: string, report?: boolean, aboutOneTask?: boolean, confirmation?: string }} Verb
 * @typedef {{ id: string, hint: string }} BranchStrategy
 * @typedef {{ id: string, label: string }} Phase
 * @typedef {{ said: string, fix: string[], retryAt?: number }} Reach
 * @typedef {{ failing?: number, count: number, nextRunAt?: number }} Jobs
 * @typedef {{ key: string, verb: string | null, broken: boolean, at: number | null }} Chip
 * @typedef {{ summary: string | null, enabled: boolean }} AutoReview
 * @typedef {{
 *   tasks: Task[], projects: string[], autoReview: AutoReview, jobs: Jobs | null, chips: Chip[], roleLogs: string[],
 *   assistant: null, reach: Reach[], verbs: Verb[], branchStrategies: BranchStrategy[],
 *   phases: Phase[], offers: Record<string, string[]>
 * }} Data
 */

/** @type {Data} */
const data = {
  tasks: [],
  projects: [],
  autoReview: { summary: "", enabled: false },
  jobs: null,
  chips: [],
  roleLogs: [],
  assistant: null,
  reach: [],
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
export const chips = () => data.chips;
export const roleLogs = () => data.roleLogs;
export const reach = () => data.reach;
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

/** @param {Partial<Data>} next */
export function set(next) {
  Object.assign(data, next);
}
