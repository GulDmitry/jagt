package dev.jagt.orchestrator.service;

import java.nio.file.Path;
import java.util.List;

/** The directories a session may write beside its worktrees, each present on disk. */
public interface SessionReach {

    List<Path> beside();
}
