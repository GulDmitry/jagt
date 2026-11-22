package dev.jagt.orchestrator.service.role;

/** A log file a role writes: the record name {@code read_record} answers it by, and its key. */
public interface RoleLog {

  String record();

  String fileKey();
}
