import { refusal } from "../core/api.js";
import { execute } from "./execute.js";
import { refresh } from "./refresh.js";
import { toast } from "./toast.js";
import { judge } from "./verdict.js";

// Tier 1 answers for itself, so there is no one message to show and nothing to say while it happens.
export async function tier1(parsed, { form, partFor, clear }) {
  const button = form.querySelector("button[type=submit]");
  button.disabled = true;
  try {
    const answered = await execute(parsed, partFor);
    if (answered.clear) clear();
  } catch (e) {
    toast(refusal(e), true); // a line that reached the backend and was refused is not tier 2's to retry
  } finally {
    button.disabled = false;
    judge();
    await refresh();
  }
}
