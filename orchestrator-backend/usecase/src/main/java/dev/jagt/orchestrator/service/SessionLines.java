package dev.jagt.orchestrator.service;

/** A line typed into a task's running session; fails where none is running. */
public interface SessionLines {

  String say(String taskId, String line);
}
