package dev.jagt.orchestrator.service;

/** A role that posts notes to a request, and whether it may post on the task's own request. */
public interface RolePosting {

  String role();

  boolean mayPostOnTasksOwnRequest();
}
