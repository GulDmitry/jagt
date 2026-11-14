package dev.jagt.orchestrator.notify;

import dev.jagt.orchestrator.port.ChannelAddress;
import dev.jagt.orchestrator.port.ChannelDriver;
import dev.jagt.orchestrator.port.ChannelMessage;
import dev.jagt.orchestrator.port.Reach;
import dev.jagt.orchestrator.port.ReachLost;
import dev.jagt.orchestrator.service.Causes;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * Posts happen off the caller's thread with a bounded backlog: a hung channel costs a banner, not a
 * task. A post that did not land is tried again after 10 s, 20 s, then every 30 s, until one does.
 */
@Component
@Slf4j
public class ChannelPosts {

  static final String DRIVER = "notification";

  private record Key(String channel, String point, String task) {}

  private record Pending(ChannelAddress address, ChannelMessage message) {}

  private final Map<Key, Pending> pending = new ConcurrentHashMap<>();
  private final List<ChannelDriver> drivers;
  private final Reach reach;
  private final Executor delivery;
  private final ScheduledExecutorService retries;

  public ChannelPosts(
      List<ChannelDriver> drivers,
      Reach reach,
      @Qualifier("notificationDelivery") Executor delivery,
      @Qualifier("notificationRetries") ScheduledExecutorService retries) {
    this.drivers = List.copyOf(drivers);
    this.reach = reach;
    this.delivery = delivery;
    this.retries = retries;
  }

  public void post(ChannelAddress address, ChannelMessage message) {
    Key key = new Key(address.name(), message.point(), String.valueOf(message.taskId()));
    if (pending.put(key, new Pending(address, message)) != null) {
      return;
    }
    try {
      delivery.execute(() -> attempt(key, 0));
    } catch (RejectedExecutionException e) {
      pending.remove(key);
      log.atWarn()
          .setMessage("notification dropped")
          .addKeyValue("channel", address.name())
          .addKeyValue("point", message.point())
          .addKeyValue("cause", "channel backed up")
          .log();
    }
  }

  private void attempt(Key key, int attempt) {
    Pending current = pending.get(key);
    if (current == null) {
      return;
    }
    ChannelAddress address = current.address();
    ChannelMessage message = current.message();
    try {
      driverOf(address).deliver(address, message);
    } catch (RuntimeException failure) {
      failed(key, current, attempt, Causes.of(failure));
      return;
    }
    if (!pending.remove(key, current)) {
      delivery.execute(() -> attempt(key, 0));
    }
    reach.reached(DRIVER, hostOf(address, message));
    log.atInfo()
        .setMessage("notification sent")
        .addKeyValue("channel", address.name())
        .addKeyValue("point", message.point())
        .addKeyValue("task", message.taskId())
        .addKeyValue("attempt", attempt + 1)
        .log();
  }

  private ChannelDriver driverOf(ChannelAddress address) {
    return drivers.stream()
        .filter(driver -> driver.kind().equals(address.kind()))
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("no driver of kind " + address.kind()));
  }

  private void failed(Key key, Pending current, int attempt, String cause) {
    ChannelAddress address = current.address();
    ChannelMessage message = current.message();
    if (attempt == 0) {
      log.atWarn()
          .setMessage("notification not delivered")
          .addKeyValue("channel", address.name())
          .addKeyValue("point", message.point())
          .addKeyValue("task", message.taskId())
          .addKeyValue("cause", cause)
          .addKeyValue("effect", "tried again until it lands")
          .log();
    }
    reach.marked(
        new ReachLost(
            DRIVER,
            hostOf(address, message),
            List.of(),
            address.name() + " cannot tell " + message.point() + ": " + cause));
    var _ =
        retries.schedule(
            () -> delivery.execute(() -> attempt(key, attempt + 1)),
            backoff(attempt),
            TimeUnit.SECONDS);
  }

  static long backoff(int attempt) {
    return switch (attempt) {
      case 0 -> 10;
      case 1 -> 20;
      default -> 30;
    };
  }

  private static String hostOf(ChannelAddress address, ChannelMessage message) {
    return address.name() + "/" + message.point();
  }
}
