package dev.jagt.orchestrator.port.channel;

/** Where a notification is handed to be told. */
public interface NotificationSink {

  void send(Notification notification);
}
