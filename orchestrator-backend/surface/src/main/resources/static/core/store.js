// Everything the page knows, in ONE place: the header, a card and the palette all answer from here, and only
// `set` writes it. A module that mutated what another one renders is a second answer to "what is on the board".

let data = {tasks: [], projects: [], autoReview: {summary: '', enabled: false}, jobs: null, verbs: []};

export const tasks = () => data.tasks;
export const projects = () => data.projects;
export const autoReview = () => data.autoReview;
export const jobs = () => data.jobs;
export const verbs = () => data.verbs;

// A human TYPES either name, and both surfaces accept both.
export const taskFor = (name) => data.tasks.find((task) => task.id === name || (task.alias || '') === name);

// What a card carries is an id, and only an id may answer it: an alias is short enough to collide with a
// numeric ticket id, and the loose lookup would hand the click to whichever task sorts first.
export const taskById = (id) => data.tasks.find((task) => task.id === id);

export function set(next) {
  data = {...data, ...next};
}
