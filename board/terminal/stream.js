export const NO_WINDOW = 4404;

/**
 * @param {{ protocol: string, host: string }} where
 * @param {{ task?: string, name?: string | null }} target
 * @param {{ cols: number, rows: number }} size
 */
export const streamUrl = (where, target, size) => {
  const query = new URLSearchParams(
    target.name ? { name: target.name } : { task: target.task ?? "" },
  );
  query.set("cols", String(size.cols));
  query.set("rows", String(size.rows));
  const scheme = where.protocol === "https:" ? "wss:" : "ws:";
  return `${scheme}//${where.host}/api/windows/stream?${query}`;
};

/** @param {string} data */
export const typed = (data) => JSON.stringify({ type: "data", data });

/** @param {{ cols: number, rows: number }} size */
export const resized = ({ cols, rows }) =>
  JSON.stringify({ type: "resize", cols, rows });
