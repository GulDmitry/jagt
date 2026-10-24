package dev.jagt.orchestrator.stand;

import java.nio.file.Path;
import java.util.Optional;

public final class StandCleanup {

    private StandCleanup() {
    }

    public static void main(String[] args) {
        Path base = Path.of(args[0]);
        boolean failedToo = args.length > 1 && args[1].equals("--failed-too");
        StandRoots.Plan plan = StandRoots.plan(base, failedToo, pid -> ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false));
        plan.stop().forEach(StandCleanup::stop);
        plan.remove().forEach(found -> StandShell.deleteRoot(found.root()));
        plan.keep().forEach(found -> System.out.println("kept failed stand run " + found.marker().scenario() + " at " + found.root()));
        System.out.println("stand cleanup: removed " + plan.remove().size() + ", kept " + plan.keep().size()
                + (plan.keep().isEmpty() ? "" : "; standClean removes those too"));
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
