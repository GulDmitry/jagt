package dev.jagt.orchestrator.port;

/** Where a notification is handed to be told. */
public interface NotificationSink {

  void send(Notification notification);
}
