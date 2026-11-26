package dev.jagt.orchestrator.service.log;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.Arrays;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Every live thread of this JVM with its state, awaited lock, lock owner and whole stack. */
@Component
public class ThreadDump {

  public String read() {
    ThreadMXBean threads = ManagementFactory.getThreadMXBean();
    return Arrays.stream(threads.dumpAllThreads(true, true))
        .map(ThreadDump::describe)
        .collect(Collectors.joining("\n"));
  }

  private static String describe(ThreadInfo thread) {
    StringBuilder text =
        new StringBuilder(
            "\"%s\" #%d %s"
                .formatted(thread.getThreadName(), thread.getThreadId(), thread.getThreadState()));
    if (thread.getLockName() != null) {
      text.append(" waiting on ").append(thread.getLockName());
    }
    if (thread.getLockOwnerName() != null) {
      text.append(
          " owned by \"%s\" #%d".formatted(thread.getLockOwnerName(), thread.getLockOwnerId()));
    }
    text.append('\n');
    for (StackTraceElement frame : thread.getStackTrace()) {
      text.append("    at ").append(frame).append('\n');
    }
    return text.toString();
  }
}
