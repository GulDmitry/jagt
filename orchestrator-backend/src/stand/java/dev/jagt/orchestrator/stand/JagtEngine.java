package dev.jagt.orchestrator.stand;

public interface JagtEngine extends AutoCloseable {

  long pid();

  int port();

  StandBoard board();

  String windows();

  String screens();

  JagtEngine restart();

  default void logs() {}

  @Override
  void close();
}
