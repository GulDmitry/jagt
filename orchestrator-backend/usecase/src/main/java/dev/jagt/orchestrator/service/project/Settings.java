package dev.jagt.orchestrator.service.project;

import dev.jagt.orchestrator.config.source.ConfigSource;
import dev.jagt.orchestrator.service.project.Setting.Kind;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

/**
 * The basic keys of {@code jagt.yml} the board shows and changes. Roles, models and secrets are not
 * among them.
 */
@Service
@RequiredArgsConstructor
public class Settings {

  public static final String PROJECTS = "projects";
  public static final String REVIEW = "review";
  public static final String TRACKER = "tracker";
  public static final String MASTER = "master";
  public static final String NOTIFICATIONS = "notifications";

  private static final List<Setting> CATALOG =
      List.of(
          Setting.of(PROJECTS, "projects", Kind.NAMES),
          Setting.of(REVIEW, "codeReview.postReviewReplies", Kind.BOOLEAN),
          Setting.of(REVIEW, "codeReview.mrTitlePattern", Kind.TEXT),
          Setting.of(REVIEW, "autoReview.enabled", Kind.BOOLEAN),
          Setting.of(REVIEW, "autoReview.windowHours", Kind.INTEGER),
          Setting.of(REVIEW, "autoReview.minIntervalMinutes", Kind.INTEGER),
          Setting.of(REVIEW, "autoReview.maxIntervalMinutes", Kind.INTEGER),
          Setting.of(REVIEW, "infraWait.backoff", Kind.LIST).needingRestart(),
          Setting.choice(TRACKER, "tracker.mode", "off", "take", "close", "both"),
          Setting.of(TRACKER, "tracker.assignee", Kind.TEXT),
          Setting.of(TRACKER, "tracker.startStatus", Kind.TEXT),
          Setting.of(TRACKER, "tracker.doneStatus", Kind.TEXT),
          Setting.of(TRACKER, "tracker.everyMinutes", Kind.INTEGER),
          Setting.choice(MASTER, "roles.master.mode", "judge", "act", "off"),
          Setting.of(MASTER, "loopBreaker.stalledRounds", Kind.INTEGER),
          Setting.of(MASTER, "loopBreaker.ships", Kind.INTEGER),
          Setting.of(MASTER, "loopBreaker.rounds", Kind.INTEGER),
          Setting.of(MASTER, "loopBreaker.perHour", Kind.INTEGER),
          Setting.of(MASTER, "conflictStallMinutes", Kind.INTEGER),
          Setting.of(NOTIFICATIONS, "notifications.channels", Kind.NAMES),
          Setting.of(NOTIFICATIONS, "notifications.points", Kind.NAMES));

  /** A setting and what the file holds for it; null where the file leaves the default. */
  public record View(Setting setting, @Nullable Object value) {}

  /** What a change said, and whether it applies only after a restart. */
  public record Change(String message, boolean restart) {}

  private final ConfigSource source;
  private final ConfigWriter writer;

  public List<View> read() {
    Object section = source.section();
    return CATALOG.stream().map(setting -> new View(setting, valueOf(section, setting))).toList();
  }

  public Change change(String key, @Nullable Object value) {
    Setting setting =
        CATALOG.stream()
            .filter(each -> each.key().equals(key))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("No setting " + key));
    List<String> path = new ArrayList<>(List.of("orchestrator"));
    path.addAll(setting.names());
    return new Change(writer.set(path, setting.accepted(value)), setting.restart());
  }

  private static @Nullable Object valueOf(@Nullable Object section, Setting setting) {
    Object at = section;
    for (String name : setting.names()) {
      at = at instanceof Map<?, ?> map ? map.get(name) : null;
    }
    return setting.kind() == Kind.NAMES && at instanceof Map<?, ?> map
        ? List.copyOf(map.keySet().stream().map(String::valueOf).toList())
        : at;
  }
}
