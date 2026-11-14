package dev.jagt.orchestrator.port.worktree;

import java.nio.file.Path;
import java.util.List;

/** The command that runs a checkout's tests, empty where its files suggest none. */
public interface BuildTools {

  List<String> testCommand(Path checkout);
}
