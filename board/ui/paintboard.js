import * as store from "../core/store.js";
import { render } from "./render.js";

/** @param {any} data */
export function paintBoard(data) {
  store.set({
    tasks: data.tasks,
    projects: data.projects || [],
    branchStrategies: data.branchStrategies || [],
    phases: data.phases || [],
    autoReview: { summary: data.autoReview, enabled: data.autoReviewEnabled },
    jobs: data.jobs,
    chips: data.chips || [],
    roleLogs: data.roleLogs || [],
    reach: data.reach || [],
    offers: data.offers || {},
  });
  render();
}
