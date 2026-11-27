import { api } from "../core/api.js";
import { t } from "../core/i18n.js";
import { paintBoard } from "./paintboard.js";
import { readThenPaint } from "./refreshread.js";
import { toast } from "./toast.js";

/** @type {{ asked: number, painted: number, boardRead: () => void }} */
const state = { asked: 0, painted: 0, boardRead: () => {} };
/** @type {Promise<void>} */
export const boardArrived = new Promise((resolve) => {
  state.boardRead = resolve;
});

export async function refresh() {
  const mine = ++state.asked;
  const failure = await readThenPaint(
    () => api("/api/tasks"),
    (data) => {
      if (mine < state.painted) return;
      state.painted = mine;
      paintBoard(data);
    },
  );
  if (failure) toast(t(failure.key, { cause: failure.cause }), true);
  state.boardRead();
}
