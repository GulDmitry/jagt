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

/** The POSIX answer: ask {@code lsof} whose working directory is in there, and kill those. */
@Component
@RequiredArgsConstructor
@Slf4j
public class LsofWorktreeProcesses implements WorktreeProcesses {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final Processes processRunner;

    /**
     * Kills processes still rooted (cwd) in a worktree about to be removed — chiefly language servers
     * (jdtls, ~1-2GB): an LSP plugin typically starts its server DETACHED (own process group, to be
     * reused across editor restarts), so it orphans and survives the agent session's death rather than
     * dying with it. Best-effort, macOS {@code lsof}; failures are logged, never thrown.
     */
    @Override
    public void reap(Path worktree) {
        try {
            reapOrThrow(worktree);
        } catch (RuntimeException e) {
            // Hygiene, not state: a machine without `lsof` (most minimal Linux images) or a kill that is
            // refused must never stop a worktree from being removed — that would turn `done` into a no-op.
            log.warn("Could not reap processes rooted in {} ({}) — removing it anyway; a language server may"
                    + " survive and hold memory", worktree, e.getMessage());
        }
    }

    private void reapOrThrow(Path worktree) {
        // Reap EVERY process whose cwd is under the worktree — NOT just java (jdtls). The agent is a Node
        // process and any of its MCP plugins may run daemons/hooks that write state into the cwd; a
        // java-only reap left those alive to repopulate the directory right after we deleted it, so the
        // worktree leaked. jagt assumes nothing about which process/plugin — cwd-under-worktree is the
        // generic, precise selector (only the task's own procs), so this handles any of them.
        var lsof = processRunner.run(null, TIMEOUT,
                List.of("lsof", "-d", "cwd", "-Fpcn"));
        // lsof reports the REAL path (symlinks resolved, e.g. macOS /var -> /private/var), so canonicalize
        // the worktree path too or the cwd comparison silently misses. Falls back to the plain absolute
        // path once the dir is already gone (a later delete pass) — nothing to reap there anyway.
        String target;
        try {
            target = worktree.toRealPath().toString();
        } catch (IOException e) {
            target = worktree.toAbsolutePath().normalize().toString();
        }
        for (Reapable r : reapable(lsof.stdout(), target)) {
            processRunner.run(null, TIMEOUT, List.of("kill", "-9", r.pid()));
            log.info("Reaped worktree-rooted process {} ({}, {})", r.pid(), r.command(), r.cwd());
        }
    }

    /** Command NEVER reaped: see {@link #reapable}. */
    private static final String VIEWER_COMMAND = "tmux";

    /** A worktree-rooted process the reap will kill — carried so the reap can log WHAT it killed. */
    record Reapable(String pid, String command, String cwd) {}

    /**
     * Picks the processes to reap from {@code lsof -d cwd -Fpcn} output: those whose cwd is at or under
     * {@code target}, EXCLUDING {@code tmux}. Field order per process set is {@code p<pid>},
     * {@code c<command>}, then {@code n<cwd>}, so the command is known by the time we see
     * the cwd.
     *
     * <p>tmux is spared because every terminal driver's viewer window runs {@code tmux attach} as its
     * foreground program, so that process's cwd sits under a worktree (kitty was even launched with
     * {@code --directory <worktree>}), and the ONE shared tmux server hosts every agent. A {@code kill -9}
     * on either closes the whole viewer window / kills all agents at once — and tmux is jagt's session
     * plumbing, never a worktree-repopulating daemon (jdtls, node MCP hooks) that the reap exists to kill.
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
