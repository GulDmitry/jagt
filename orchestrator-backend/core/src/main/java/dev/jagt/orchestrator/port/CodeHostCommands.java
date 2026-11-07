package dev.jagt.orchestrator.port;

import java.util.Map;
import java.util.Set;

/**
 * What one code host's command-line tool contributes to judging a shell line: the word that runs
 * it, which verbs of which group only read, and every command group it answers to.
 */
public interface CodeHostCommands {

  /** The bare program name, as typed on a line. */
  String command();

  /** The verbs that only read, per command group; any other verb of a group is a write. */
  Map<String, Set<String>> reads();

  /** Every command group the tool has, a reading one or not. */
  Set<String> groups();
}
