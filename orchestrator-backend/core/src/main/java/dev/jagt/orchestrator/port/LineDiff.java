package dev.jagt.orchestrator.port;

public interface LineDiff {

  LineChange between(String earlier, String later);
}
