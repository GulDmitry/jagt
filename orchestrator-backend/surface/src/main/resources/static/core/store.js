// Everything the page knows, in ONE place: a module that mutated what another renders is a second answer.

import {bothNames} from './format.js';

let data = {tasks: [], projects: [], autoReview: {summary: '', enabled: false}, jobs: null, actStop: null,
  masterMode: 'off',
  verbs: [],
  branchStrategies: [], phases: [], offers: {}};

export const tasks = () => data.tasks;
export const projects = () => data.projects;
export const branchStrategies = () => data.branchStrategies;
export const phases = () => data.phases;
export const autoReview = () => data.autoReview;
export const jobs = () => data.jobs;
export const actStop = () => data.actStop;
export const masterMode = () => data.masterMode;
export const verbs = () => data.verbs;
export const offersOn = (task) => data.offers[task.id] || [];

// The verb a task's card offers for a tab: the server's verb says which tab it feeds, by its part.
export const offerFor = (task, part) => data.verbs.find((verb) => verb.part === part && offersOn(task).includes(verb.id));

export const taskFor = (name) => data.tasks.find((task) => task.id === name || (task.alias || '') === name);


// An empty reference names no task: the loose lookup above would hand it the first task without an alias.
export const nameOf = (ref) => {
  const task = ref ? taskFor(ref) : null;
  return task ? bothNames(task) : ref;
};

// Only an id may answer a click: an alias is short enough to collide with a numeric ticket id.
export {bothNames};

export const taskById = (id) => data.tasks.find((task) => task.id === id);

export function set(next) {
  data = {...data, ...next};
}
