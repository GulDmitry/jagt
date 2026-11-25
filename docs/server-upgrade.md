[← jagt on a server](server.md)

# Upgrade, back up, roll back

What `/data` (`ORCHESTRATOR_DATA`) holds that outlives an image:

| path | what | when it changes |
|------|------|-----------------|
| `jagt.yml` | the configuration, seeded from the image on a first start | when you or the board edit it |
| `jagt/state.json` | every task, written atomically | every mutation |
| `jagt/state.json.bak` | the version before the last write | every mutation |
| `jagt/state.json.corrupt` | an unreadable primary, set aside while `.bak` stood in | on that recovery |
| `tasks/`, `projects/` | task worktrees (unpushed commits live here), clones | while sessions work |
| `jagt/master-token`, `logs/` | the Master's token (drawn at each start), logs | each start |

- **Read across minors**: a newer jar reads an older `state.json` as it stands: unknown keys are ignored, a missing number or flag
  reads 0 or false; the next mutation rewrites it in the newer shape and keeps the old one as `.bak`
  (`StateServiceCompatibilityTest` holds a fixture of every shipped minor, 0.9 to 0.23).
- **Unreadable state**: `.bak` takes over and the log says so; with no usable `.bak` jagt refuses to start, never starts empty.
- **Older jar, newer file**: untested, unknown. Roll back from a copy, never by pointing the old jar at the new file.

Before an upgrade, stop jagt, then copy the folder (with sessions running only `state.json` is a consistent copy):

```sh
docker compose stop                    # or: kubectl scale deployment/<name> --replicas=0
docker run --rm -v <volume>:/data -v "$PWD":/backup alpine tar czf /backup/jagt-data.tgz -C /data .   # <volume>: docker volume ls
cp "$ORCHESTRATOR_DATA"/jagt/state.json* ~/backup/    # a jar install: the same files in its data folder
```

Roll back: stop, put the previous image tag (or jar) back, restore the copy over `/data`, start, and check readiness and the task list.
Keep the running minor's image tag before pulling the next one; worktrees made after the copy are not in it.
