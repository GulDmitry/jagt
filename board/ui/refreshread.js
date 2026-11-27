import { cause } from "../core/api.js";

/**
 * @template T
 * @param {() => Promise<T>} read
 * @param {(data: T) => void} paint
 * @returns {Promise<{ key: "refresh.backend" | "refresh.render", cause: string } | null>}
 */
export async function readThenPaint(read, paint) {
  let data;
  try {
    data = await read();
  } catch (e) {
    return { key: "refresh.backend", cause: cause(e) };
  }
  try {
    paint(data);
  } catch (e) {
    return { key: "refresh.render", cause: cause(e) };
  }
  return null;
}
