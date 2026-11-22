package dev.jagt.orchestrator.adapter.process;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/** The one place a child process starts: whoever closes it ends every child still alive. */
public final class ChildProcesses implements AutoCloseable {

  private final Set<Process> running = ConcurrentHashMap.newKeySet();
  private final Set<Process> stopped = ConcurrentHashMap.newKeySet();
  private volatile boolean closed;

  public Process spawn(ProcessBuilder builder) throws IOException {
    Process started = builder.start();
    running.add(started);
    CompletableFuture<Void> _ = started.onExit().thenAccept(running::remove);
    if (closed) {
      stopped.add(started);
      ProcessTrees.killAtOnce(started, "shutdown");
    }
    return started;
  }

  /** Outlives its owner by design: never registered, never ended at close. */
  public Process spawnDetached(ProcessBuilder builder) throws IOException {
    return builder.start();
  }

  public void release(Process process) {
    running.remove(process);
  }

  public void kill(Process process, String reason) {
    ProcessTrees.killAtOnce(process, reason);
  }

  /** Whether {@link #close()} ended this child; asking forgets it. */
  public boolean wasStopped(Process process) {
    return stopped.remove(process);
  }

  public boolean isClosed() {
    return closed;
  }

  /** A paused context resumes through its lifecycle's start, so a close that was a stop ends. */
  public void reopen() {
    closed = false;
  }

  @Override
  public void close() {
    closed = true;
    List<Process> live = List.copyOf(running);
    stopped.addAll(live);
    ProcessTrees.endAfterGrace(live);
  }
}
