import { api } from "../core/api.js";
import { t } from "../core/i18n.js";
import { sending } from "./submit.js";

export const interpret = (form, text, done) =>
  sending(form, {
    waiting: t("palette.interpreting"), // a model call: seconds
    send: () =>
      api("/api/interpret", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ text }),
      }),
    done,
  });
