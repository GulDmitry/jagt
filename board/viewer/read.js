import { api } from "../core/api.js";
import { files } from "./patch.js";

const task = (id) => `task=${encodeURIComponent(id)}`;

export const tree = (id) => api(`/api/tasks/code/tree?${task(id)}`);

export const changes = async (id) =>
  (await api(`/api/tasks/diff?${task(id)}`)).map((repo) => ({
    project: repo.project,
    cut: repo.cut,
    files: files(repo.diff),
  }));

export const file = (id, { project, path }) =>
  api(
    `/api/tasks/code/file?${task(id)}&project=${encodeURIComponent(project)}&path=${encodeURIComponent(path)}`,
  );

export const search = (id, project, q) =>
  api(
    `/api/tasks/code/search?${task(id)}&project=${encodeURIComponent(project)}&q=${encodeURIComponent(q)}`,
  );
