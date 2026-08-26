# Troubleshooting

[← README](../README.md)

## Agents

| symptom | fix |
|---------|-----|
| Task stuck at `SHIPPING`, no request appears | `ship <ticket>` again — jagt respawns the dead agent to finish |
| Agent seems hung | `focus <ticket>` to look; `respawn <ticket>` if the session is dead |
| `API Error: 529 Overloaded` | transient, server-side. Re-run; task state is unchanged |
| Nothing pastes in a kitty window | non-UTF-8 shell locale — see [Installation](installation.md#notes) |

The agent that stops without saying so is caught by three signs, none of which costs a token: its own
`outcome=question` report (NEEDS INPUT on the card), the hooks its CLI fires when it stops or waits, and the
log a session keeps of itself. Terminal output is the last resort, and only because a long tool call writes
nothing anywhere else.

| symptom | fix |
|---------|-----|
| A session at a prompt is never flagged | Hooks are written at `initialize_task`; an older worktree reports nothing until the task is recreated |
| No session ever reports | `curl` is not on the session's PATH. The flag still arrives, one `staleAfter` window later |

## Deploy and revert

| symptom | fix |
|---------|-----|
| `deploy` says `MERGE CONFLICT` | `ide <ticket>` opens the **deploy** worktree — resolve, `git add`, `deploy` again |
| `revert` has no record of the merge commit | do it by hand (below) |
| `revert` says the revert conflicts | jagt aborts and pushes nothing; revert by hand and decide |

Nothing is pushed on a conflicting deploy, and the task goes `DEPLOY_CONFLICT`. Your task branch and its
review request are untouched.

For a deploy made before jagt recorded merge commits, it will not guess which merge to revert on a shared
branch:

```sh
git log --merges --grep ABC-42 origin/dev   # find it
git revert -m 1 <sha> && git push
```

Deploys made since are revertible with one command.

## Startup and the jar

| symptom | fix |
|---------|-----|
| jagt refuses to start and prints a numbered list | fix the whole list — each line names the key |
| It refuses on Linux over `orchestrator.platform` | the default is `macos`, and it is a choice rather than a detection: set it to `linux` |
| The jar exits at once, saying nothing new | usually a port still held: `lsof -ti tcp:8290 \| xargs kill` |
| An endpoint answers 500 while `/` works | you rebuilt while it ran. Restart from the staged jar |

> [!IMPORTANT]
> `./gradlew build` rewrites `jagt.jar` **in place**, so a running JVM keeps reading a file whose content
> changed: already-loaded classes work, everything else dies with `NoClassDefFoundError`. Run
> `./gradlew stageJar && java -jar build/libs/jagt-run.jar` — staging writes a new file each time. jagt also
> notices this itself within a minute and says so.

A `NoClassDefFoundError` during a startup failure or on exit is the same cause, not a code bug. The missing
class varies precisely because no single class is the problem.

## The board and the log

| symptom | fix |
|---------|-----|
| Focus raises no window | `grep kitty jagt-backend.log` — install kitty, or set `orchestrator.kittyCommand` |
| You cannot find the log | `tail -f jagt-backend.log` next to where you started the jar |

Copy the log before restarting: **every start empties it and deletes the archives beside it**, so what you
read is always the running session.

## State on disk

| symptom | what jagt does |
|---------|----------------|
| `state.json` is corrupted | recovers from `state.json.bak`, moves the bad file to `state.json.corrupt` |
| Both the file and its backup are unusable | **refuses to start** rather than begin with an empty task list |
| A worktree directory nobody owns is left on disk | pings you once at startup and WARNs — it never deletes it |

An orphaned worktree can hold uncommitted work and copies of your secrets (`worktree.copyGlobs`), so removing
it is your call. The log names each directory and how many copied secret files are inside.
