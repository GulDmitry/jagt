package dev.jagt.orchestrator.adapter.process;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
final class ProcessTrees {

  private static final Duration GRACE = Duration.ofSeconds(2);

  private ProcessTrees() {}

  /** Listed first: a descendant whose parent died is reparented and no longer reachable from it. */
  static void killAtOnce(Process process, String reason) {
    List<ProcessHandle> descendants = process.descendants().toList();
    process.destroyForcibly();
    descendants.forEach(ProcessHandle::destroyForcibly);
    log.atWarn()
        .setMessage("process killed")
        .addKeyValue("pid", process.pid())
        .addKeyValue("descendants", descendants.size())
        .addKeyValue("reason", reason)
        .log();
  }

  /** Asked to end, then killed once the grace has passed, each tree to its last descendant. */
  static void endAfterGrace(List<Process> live) {
    Map<Process, List<ProcessHandle>> trees = new LinkedHashMap<>();
    live.forEach(process -> trees.put(process, process.descendants().toList()));
    trees.forEach((process, descendants) -> {
      descendants.forEach(ProcessHandle::destroy);
      process.destroy();
    });
    trees.forEach((process, descendants) -> {
      awaitExit(process, descendants);
      killSurvivors(process, descendants);
    });
  }

  private static void awaitExit(Process process, List<ProcessHandle> descendants) {
    try {
      process.waitFor(GRACE.toMillis(), TimeUnit.MILLISECONDS);
      for (ProcessHandle each : descendants) {
        each.onExit().get(GRACE.toMillis(), TimeUnit.MILLISECONDS);
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } catch (ExecutionException | TimeoutException e) {
      // what is still alive is killed next
    }
  }

  private static void killSurvivors(Process process, List<ProcessHandle> descendants) {
    List<ProcessHandle> alive = descendants.stream().filter(ProcessHandle::isAlive).toList();
    if (process.isAlive() || !alive.isEmpty()) {
      process.destroyForcibly();
      alive.forEach(ProcessHandle::destroyForcibly);
    }
    awaitExit(process, alive);
    log.atWarn()
        .setMessage("process killed")
        .addKeyValue("pid", process.pid())
        .addKeyValue("descendants", descendants.size())
        .addKeyValue("reason", "shutdown")
        .log();
  }
}
