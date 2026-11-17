# Troubleshooting

[← README](../README.md)

## Agents

| symptom | cause | fix |
|---------|-------|-----|
| Stuck at `SHIPPING`, no request appears | the agent died mid-ship | `ship <ticket>` again; jagt respawns it |
| Agent seems hung | a long tool call writes nothing anywhere | `focus <ticket>` to look, `respawn <ticket>` if dead |
| `API Error: 529 Overloaded` | transient, server-side | re-run; task state is unchanged |
| Nothing pastes in a kitty window | non-UTF-8 shell locale | [Installation](installation.md#notes) |
| A session at a prompt is never flagged | hooks are written at `initialize_task` | recreate the task; an older worktree reports nothing |
| No session ever reports | `curl` is not on the session's PATH | nothing: the flag arrives one `staleAfter` window later |

Three token-free signs catch an agent that stopped without saying so: its own `outcome=question` report
(NEEDS INPUT on the card), the hooks its CLI fires, and the log a session keeps. Terminal output is the last
resort.

## Deploy and revert

| symptom | fix |
|---------|-----|
| `deploy` says `MERGE CONFLICT` | nothing was pushed; `code <ticket>` opens the **deploy** worktree — resolve, `git add`, `deploy` again |
| `revert` has no record of the merge commit | the deploy predates jagt recording them: by hand, below |
| `revert` says the revert conflicts | nothing is pushed; resolve in the `<task>-revert` worktree, `git add`, then `revert` again |

A conflicting deploy leaves the task in `DEPLOY_CONFLICT`, with your task branch and its review request
untouched. On a shared branch jagt will not guess which merge to revert:

```sh
git log --merges --grep ABC-42 origin/dev   # find it
git revert -m 1 <sha> && git push
```

Deploys made since are revertible with one command.

## Startup and the jar

| symptom | cause | fix |
|---------|-------|-----|
| It refuses to start, printing a numbered list | the installation is incomplete | fix the whole list — each line names the key |
| It refuses over `orchestrator.platform` | the key overrides the detected OS and names another one | delete the key |
| The jar exits at once, saying nothing new | usually a port still held | `lsof -ti tcp:8290 \| xargs kill` |
| An endpoint answers 500 while `/` works | you rebuilt while it ran | press `restart` (`supervisor: launchd`), else restart from the staged jar |
| `localhost:8290` shows "jagt cannot start" | the start was refused | the page lists every problem and edits `jagt.yml`; saving starts again |

> [!IMPORTANT]
> `./gradlew build` rewrites `jagt.jar` **in place**, so a running JVM keeps reading a file whose content
> changed: already-loaded classes work, everything else dies with `NoClassDefFoundError`. Run
> `./gradlew stageJar && java -jar build/libs/jagt-run.jar` — staging writes a new file each time. jagt also
> notices this itself within a minute and says so.

A `NoClassDefFoundError` during a startup failure or on exit is the same cause, not a code bug.

## The board and the log

| symptom | fix |
|---------|-----|
| Focus raises no window | `grep kitty <root>/data/logs/jagt-backend.log` — install kitty, or set `orchestrator.kittyCommand` |
| You cannot find the log | `tail -f <root>/data/logs/jagt-backend.log`, in `logs/` of the data folder |
| A session went missing and you want to know when | `grep -E 'session window changed|session gone|task window' <root>/data/logs/jagt-backend.log`: one line per change |

The log rolls at 10 MB into one `.1`. A start keeps the previous run as `jagt-backend.log.<UTC date-time>`
for 14 days, 200 MB at most; a read of an earlier run goes there.

## State on disk

| symptom | what jagt does |
|---------|----------------|
| `state.json` of an older version sits in the repository root or `~/.jagt` | moves it into the data folder once, naming it in the log |
| `state.json` is corrupted | recovers from `state.json.bak`, moves the bad file to `state.json.corrupt` |
| Both the file and its backup are unusable | **refuses to start** rather than begin with an empty task list |
| A worktree nobody owns still holds files | pings you once at startup and WARNs; it never deletes it |
| Only the IDE's files are left where a worktree was | deletes it at the next start, one INFO line |
| A worktree you cut yourself sits next to the project | says nothing: it carries no file of jagt's |

Removing an orphaned worktree that still holds something is your call — it can hold uncommitted work and copies
of your secrets (`worktree.copyGlobs`). The log names each directory and how many copied secret files are inside.
