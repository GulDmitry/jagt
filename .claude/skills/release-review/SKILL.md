---
name: release-review
description: A global review — architecture, security, tests, specs — before a release or after work spanning many commits, judged against what jagt already decided so no round reopens a settled question. Use when asked for a full, architectural or security review of the tree.
---

# A round is judged against what is decided

Range: the last `Release` commit to `HEAD` unless one is named (`git log --grep='^Release ' -1 --format=%h`).

1. **Every reviewer gets what is decided**, not only the code:
   - `AGENTS.md`, `ARCHITECTURE.md`, `docs/rules/*.md`; the security reviewer `docs/rules/guards.md` first.
   - `docs/decisions/README.md`, then every record a finding touches: its Rejected and Reopen when.
   - `git log --format=%s <range>`: each subject is a finding already fixed. Proposing its reverse needs a
     `reopens` or `breaks` class like any other finding.
   - The range's diff, and the files it touches in full.
2. **One reviewer per dimension**: architecture (kinds, rings, collaborators), security (the guards), tests
   (RED-proven, light, necessary), specs and docs (each says what the code does).
3. **A finding names its class**, or it is dropped:
   - `new` — no rule, record or commit subject in the range covers it;
   - `reopens NNNN` — quotes the record's Reopen when line that now holds, with the evidence;
   - `breaks <file>` — quotes the rule in `AGENTS.md`, `docs/rules/` or a spec the code breaks.

   Proposing a record's Rejected option, or a new spelling of a class `guards.md` lists, is `known`: dropped.
4. **A verifier on a light model checks each class**: the quoted line exists and says that, the commit subject is
   not the same finding. The heavy model judges only what survives.
5. **Every survivor ends committed, as one of three**:
   - fixed, a RED-proven test wherever code changed;
   - rejected: its why goes where the next round meets it — one comment at the site, or a new record's Rejected row;
   - a class an earlier round found too: a test asserting it (`RingsTest`, `KindsTest`, `MockCeilingTest`, or a new
     one beside them), never a third finding.
6. **A guard gap of a new class** joins `guards.md` with its Reopen condition, in the commit refusing what it can.
7. Report per dimension: new, reopened, broken, dropped as known — then each open finding in one line.
