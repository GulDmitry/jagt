import { verdictOf } from "../core/grammar.js";
import * as store from "../core/store.js";

export const ask = document.getElementById("ask");
const verdict = document.getElementById("palette-state");

export function refreshSuggestions() {
  document.getElementById("ask-options").replaceChildren(
    ...store.verbs().map((verb) =>
      Object.assign(document.createElement("option"), {
        value: verb.id,
        label: verb.hint,
      }),
    ),
  );
}

export function judge() {
  const { kind, text } = verdictOf(ask.value.trim());
  verdict.classList.remove("ok", "bad");
  if (kind) verdict.classList.add(kind);
  verdict.textContent = text;
}
