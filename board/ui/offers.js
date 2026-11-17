import * as store from "../core/store.js";
import { blocked } from "./inflight.js";

// An offer whose verb has not arrived says nothing: its part is the verb's.
/** @param {import("../core/store.js").Task} task */
export const offersOn = (task) =>
  store.offersOn(task).flatMap((id) => {
    const verb = store.verbs().find((each) => each.id === id);
    return verb ? [{ id, hint: verb.hint, part: verb.part }] : [];
  });

/** @param {import("../core/store.js").Task} task */
export const blockedOn = (task) =>
  new Set(
    (task.actions || [])
      .filter((action) => blocked(task, action))
      .map((action) => action.id),
  );
