// Everything the page knows, in ONE place: a module that mutated what another renders is a second answer.

let data = {tasks: [], projects: [], autoReview: {summary: '', enabled: false}, jobs: null, verbs: [],
  branchStrategies: [], phases: []};

export const tasks = () => data.tasks;
export const projects = () => data.projects;
export const branchStrategies = () => data.branchStrategies;
export const phases = () => data.phases;
export const autoReview = () => data.autoReview;
export const jobs = () => data.jobs;
export const verbs = () => data.verbs;

export const taskFor = (name) => data.tasks.find((task) => task.id === name || (task.alias || '') === name);

// An empty reference names no task: the loose lookup above would hand it the first task without an alias.
export const nameOf = (ref) => {
  const task = ref ? taskFor(ref) : null;
  if (!task) return ref;
  return task.alias ? `${task.alias} \u00b7 ${task.id}` : task.id;
};

// Only an id may answer a click: an alias is short enough to collide with a numeric ticket id.
export const taskById = (id) => data.tasks.find((task) => task.id === id);

export function set(next) {
  data = {...data, ...next};
}
