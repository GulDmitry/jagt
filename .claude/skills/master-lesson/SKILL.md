---
name: master-lesson
description: Turn a defect the human caught after the Master passed it into a pattern the Master stops on, proven by a masterEval round. Use when the human pastes an exchange and says it must not happen again.
---

# A miss becomes a pattern, proven RED then GREEN

Input: the exchange (what the agent wrote, what the human said) and the human's why.

1. **Name the class, not the case.** The rule the unit broke, in two to four words — `leaked abstraction`, not
   "ack-mode comment". Never a project, class, framework or vendor from the case.
2. **Sharpen before adding.** Read `## Patterns that stop a round` in `master-brief.md.dist`: a pattern already
   covering it gets its line sharpened; only a new class gets a new line. One line per pattern: what a unit must
   not do, and one clause of why.
3. **One round in `MasterCase.matrix()`.** Invented names, smallest diff carrying the pattern and nothing else that
   blocks. `names`: the offending file plus the file proving the premise, so it cannot pass on another finding.
4. **RED on the old brief.** `git show HEAD:master-brief.md.dist` into the scratchpad, then
   `./gradlew masterEval -PmasterBrief=<that file>`: the new round fails. Green there means the brief was not what
   missed it — the role's model, or the author's `<self_review>` — say so and stop.
5. **GREEN on the new brief.** `./gradlew masterEval`: every round passes, the old ones included.
6. **Copy the pattern line into the local `master-brief.md`**, gitignored, so this install judges by it.
7. Commit `master-brief.md.dist` and `MasterCase.java` together; the subject names the class that slipped through.
