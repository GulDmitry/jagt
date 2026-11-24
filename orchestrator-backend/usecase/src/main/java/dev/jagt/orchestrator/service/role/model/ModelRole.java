package dev.jagt.orchestrator.service.role.model;

/** A role a model runs; each role module declares its own, and the board shows it while it runs. */
public interface ModelRole {

  String id();

  boolean running();
}
