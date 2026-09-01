package dev.jagt.orchestrator.adapter;

import dev.jagt.orchestrator.port.Processes;
import dev.jagt.orchestrator.port.WorktreeProcesses;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class LsofWorktreeProcesses implements WorktreeProcesses {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final Processes processRunner;

    /** An LSP plugin typically starts its server DETACHED, so it orphans and outlives the session that spawned it. */
    @Override
    public void reap(Path worktree) {
        try {
            reapOrThrow(worktree);
        } catch (RuntimeException e) {
            // Hygiene, not state: a missing `lsof` or a refused kill must never stop the removal.
            log.atWarn().setMessage("worktree process reap failed")
                    .addKeyValue("path", worktree)
                    .addKeyValue("cause", e.toString())
                    .addKeyValue("effect", "removed anyway")
                    .log();
        }
    }

    private void reapOrThrow(Path worktree) {
        // cwd-under-worktree is the precise selector: a daemon left alive repopulates the directory after
        // it is deleted.
        var lsof = processRunner.run(null, TIMEOUT,
                List.of("lsof", "-d", "cwd", "-Fpcn"));
        // lsof reports the REAL path (macOS /var -> /private/var), so canonicalize or the comparison silently
        // misses. A dir already gone has nothing to reap, so the plain absolute path will do.
        String target;
        try {
            target = worktree.toRealPath().toString();
        } catch (IOException e) {
            target = worktree.toAbsolutePath().normalize().toString();
        }
        for (Reapable r : reapable(lsof.stdout(), target)) {
            processRunner.run(null, TIMEOUT, List.of("kill", "-9", r.pid()));
            log.atInfo().setMessage("worktree process reaped")
                    .addKeyValue("pid", r.pid())
                    .addKeyValue("cmd", r.command())
                    .addKeyValue("path", r.cwd())
                    .log();
        }
    }

    /** Command NEVER reaped: see {@link #reapable}. */
    private static final String VIEWER_COMMAND = "tmux";

    record Reapable(String pid, String command, String cwd) {}

    /**
     * {@code lsof -d cwd -Fpcn} emits {@code p<pid>}, {@code c<command>}, then {@code n<cwd>}, so the command is
     * known by the time the cwd arrives. tmux is spared: a viewer runs {@code tmux attach} with its cwd under a
     * worktree, and the ONE shared server hosts every agent, so a {@code kill -9} takes them all down.
     */
    static List<Reapable> reapable(String lsofOutput, String target) {
        List<Reapable> reapable = new java.util.ArrayList<>();
        String pid = null;
        String command = null;
        for (String line : lsofOutput.lines().toList()) {
            if (line.startsWith("p")) {
                pid = line.substring(1);
                command = null;
            } else if (line.startsWith("c")) {
                command = line.substring(1);
            } else if (line.startsWith("n") && pid != null) {
                String cwd = line.substring(1);
                boolean underWorktree = cwd.equals(target) || cwd.startsWith(target + "/");
                if (underWorktree && !VIEWER_COMMAND.equals(command)) {
                    reapable.add(new Reapable(pid, command, cwd));
                    pid = null;
                }
            }
        }
        return reapable;
    }
}
