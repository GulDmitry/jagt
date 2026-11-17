import { api } from "../core/api.js";
import { files } from "./patch.js";

/**
 * @typedef {{ project: string, files: string[] }} Tree
 * @typedef {{ project: string, cut?: boolean, files: Changed[] }} Repo
 * @typedef {{ kind: string, path: string, text: string }} Content
 * @typedef {{ path: string, line: number, text: string }} Hit
 * @typedef {{ hits: Hit[], cut?: boolean }} Found
 * @typedef {{ project: string, path: string, line: number, change?: Changed }} Picked
 * @typedef {import("./patch.js").Changed} Changed
 */

/** @param {string} id */
const task = (id) => `task=${encodeURIComponent(id)}`;

/**
 * @param {string} id
 * @returns {Promise<Tree[]>}
 */
export const tree = (id) => api(`/api/tasks/code/tree?${task(id)}`);

/**
 * @param {string} id
 * @returns {Promise<Repo[]>}
 */
export const changes = async (id) =>
  (await api(`/api/tasks/diff?${task(id)}`)).map(
    /** @param {{ project: string, cut?: boolean, diff: string }} repo */ (
      repo,
    ) => ({
      project: repo.project,
      cut: repo.cut,
      files: files(repo.diff),
    }),
  );

/**
 * @param {string} id
 * @param {{ project: string, path: string }} picked
 * @returns {Promise<Content>}
 */
export const file = (id, { project, path }) =>
  api(
    `/api/tasks/code/file?${task(id)}&project=${encodeURIComponent(project)}&path=${encodeURIComponent(path)}`,
  );

/**
 * @param {string} id
 * @param {string} project
 * @param {string} q
 * @returns {Promise<Found>}
 */
export const search = (id, project, q) =>
  api(
    `/api/tasks/code/search?${task(id)}&project=${encodeURIComponent(project)}&q=${encodeURIComponent(q)}`,
  );
