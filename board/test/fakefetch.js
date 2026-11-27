import { mock } from "node:test";

/** @param {Record<string, object>} files */
export const serveFiles = (files) =>
  mock.method(globalThis, "fetch", async (/** @type {string} */ url) =>
    url in files
      ? new Response(JSON.stringify(files[url]))
      : new Response("", { status: 404 }),
  );
