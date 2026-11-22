package dev.jagt.orchestrator.port;

/** Whether git finds every request of a task merged, as a read just made and recorded. */
@FunctionalInterface
public interface MergedRefresh {

  boolean refresh(String taskId);
}
