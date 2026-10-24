package dev.jagt.orchestrator.stand;

import java.nio.file.Path;
import java.util.Optional;
import java.util.function.LongPredicate;

public final class StandCleanup {

    private StandCleanup() {
    }

    public static void main(String[] args) {
        Path base = Path.of(args[0]);
        Path failedDir = Path.of(args[1]);
        LongPredicate alive = pid -> ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false);
        StandRoots.Plan plan = StandRoots.plan(base, alive);
        plan.dead().forEach(StandCleanup::stop);
        StandTraces.Removed removed = sweep(plan, failedDir, StandTraces.ofTheHuman(base), alive);
        System.out.println("stand cleanup: removed " + plan.dead().size() + " roots, " + removed.trust()
                + " trust entries, " + removed.transcripts() + " transcript directories");
    }

    static StandTraces.Removed sweep(StandRoots.Plan plan, Path failedDir, StandTraces traces, LongPredicate alive) {
        for (StandRoots.Found found : plan.dead()) {
            if (found.marker().failed()) {
                System.out.println("failed stand run " + found.marker().scenario() + " evidence: "
                        + StandEvidence.keep(found.root(), found.marker().scenario(), failedDir).toAbsolutePath());
            }
            StandShell.deleteRoot(found.root());
        }
        return traces.remove(root -> !alive.test(StandRoots.pidOf(root)));
    }

    private static void stop(StandRoots.Found found) {
        StandMarker marker = found.marker();
        jagt(marker).ifPresent(StandShell::stopTree);
        StandShell.killTmux(found.root());
    }

    private static Optional<ProcessHandle> jagt(StandMarker marker) {
        String port = "--server.port=" + marker.port();
        return ProcessHandle.of(marker.jagtPid()).filter(ProcessHandle::isAlive)
                .filter(handle -> handle.info().arguments().map(arguments -> java.util.List.of(arguments).contains(port))
                        .orElse(false));
    }
}
