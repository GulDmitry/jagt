package dev.jagt.orchestrator.port;

import lombok.With;
import org.jspecify.annotations.Nullable;

/**
 * Something a human must be told, carrying the id of the point it stands for so a channel can route
 * on that.
 */
public record Notification(
    Topic topic,
    @With String point,
    @Nullable String taskId,
    @With @Nullable String alias,
    String title,
    String body) {

  /**
   * What the notification is ABOUT. A channel takes or leaves a notification by this, not by its
   * wording.
   */
  public enum Topic {
    /** The agent needs the human: a question, a report, a round that ended. */
    AGENT,
    /** An agent that should be working has gone quiet. */
    WATCHDOG,
    /**
     * Nothing is wrong with the work — something on disk or in the install wants a human
     * eventually.
     */
    HOUSEKEEPING,
    /** jagt itself cannot go on correctly until the human acts. */
    INSTALL,
    /** A subscription window filled up, or paid usage is being spent. */
    USAGE
  }

  public static Notification fromAgent(
      String point, @Nullable String taskId, String title, String body) {
    return new Notification(Topic.AGENT, point, taskId, null, title, body);
  }

  public static Notification watchdog(String point, String taskId, String title, String body) {
    return new Notification(Topic.WATCHDOG, point, taskId, null, title, body);
  }

  public static Notification housekeeping(String point, String title, String body) {
    return new Notification(Topic.HOUSEKEEPING, point, null, null, title, body);
  }

  public static Notification install(String point, String title, String body) {
    return new Notification(Topic.INSTALL, point, null, null, title, body);
  }

  public static Notification usage(String point, String title, String body) {
    return new Notification(Topic.USAGE, point, null, null, title, body);
  }

  public static Notification requestOpened(String point, String taskId, String body) {
    return new Notification(Topic.AGENT, point, taskId, null, "request opened", body);
  }

  public static Notification deployed(String point, String taskId, String body) {
    return new Notification(Topic.AGENT, point, taskId, null, "deployed", body);
  }
}
