// Everything the page knows, in ONE place: the header, a card and the palette all answer from here, and only
// `set` writes it. A module that mutated what another one renders is a second answer to "what is on the board".

let data = {tasks: [], projects: [], autoReview: {summary: '', enabled: false}, jobs: null, verbs: []};

export const tasks = () => data.tasks;
export const projects = () => data.projects;
export const autoReview = () => data.autoReview;
export const jobs = () => data.jobs;
export const verbs = () => data.verbs;

// A human addresses a task by either name, and both surfaces accept both.
export const taskFor = (name) => data.tasks.find((task) => task.id === name || (task.alias || '') === name);

export function set(next) {
  data = {...data, ...next};
}
