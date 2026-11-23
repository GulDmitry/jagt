---
name: code-reviewer-lesson
description: Turn a pattern the code reviewer's notes are rated low on, or a defect it passed, into a brief line proven by a roleEval case. Use when the dated statistics show a note kind with dislikes, or a human pastes a request the reviewer got wrong.
---

# A rating becomes a case, then a brief line

A rating never rewrites the brief by itself: it names a pattern, a case proves it, and a person reads the diff.

Input: the dated lines of `<data>/records/code-review.jsonl` for one kind of note, or the exchange (the request's text, the
note, the human's why).

1. **Name the class, not the case.** The defect kind in two to four words, `unguarded retry`, never a project, class, framework
   or vendor from the case. Several dislikes on one kind mean noise to cut; a defect the reviewer passed means a kind to add.
2. **Sharpen before adding.** Read `## What you look for` in `roles/code-reviewer/brief.md.dist`: a row already covering the
   class gets sharpened; only a new class gets a new line. A line states one fact a reader answers yes or no on the diff.
3. **One case in `roles/code-reviewer/eval/NN-<slug>.yml`.** Invented names, the smallest diff carrying the pattern and nothing
   else that blocks. `expect.findings` names the file and a `pattern` regex of the defect's words, so it cannot pass on another
   finding; a note to cut is a `verdict: ready` case holding the rated shape. A case never reuses a scenario the brief shows.
4. **RED on the old brief.** `git show HEAD:roles/code-reviewer/brief.md.dist` into the scratchpad, then
   `./gradlew roleEval -Prole=code-reviewer -ProleBrief=<that file>`: the new case fails. Green there means the brief was not
   what missed it, the model was: say so and stop.
5. **GREEN on the new brief.** `./gradlew roleEval -Prole=code-reviewer`: every case passes, the old ones included.
6. **Copy the line into the local `roles/code-reviewer/brief.md`**, gitignored, so this install judges by it.
7. Commit `brief.md.dist` and the case's `.yml` together; the subject names the class the reviewer got wrong.
