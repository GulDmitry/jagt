package dev.jagt.orchestrator.port.worktree;

public interface LineDiff {

  LineChange between(String earlier, String later);
}
