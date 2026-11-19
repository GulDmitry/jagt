package dev.jagt.orchestrator.command;

import org.jspecify.annotations.Nullable;

/** What the root session may press: the verbs a human presses, stamped with the root's origin. */
public interface RootPresses {

  String press(String taskIdOrAlias, String verb, @Nullable String project, @Nullable String line);

  String run(String verb, String tail);
}
