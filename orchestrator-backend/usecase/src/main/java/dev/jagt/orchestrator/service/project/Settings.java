package dev.jagt.orchestrator.service.project;

import dev.jagt.orchestrator.config.InfraWaitProperties;
import dev.jagt.orchestrator.config.source.ConfigSource;
import dev.jagt.orchestrator.service.ConfigFile;
import dev.jagt.orchestrator.service.project.Setting.Kind;
import dev.jagt.orchestrator.service.role.RoleSettingKeys;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.stereotype.Service;

/**
 * The basic keys of {@code jagt.yml} the board shows and changes, and those the installed roles
 * contribute. Models and secrets are not among them.
 */
@Service
@RequiredArgsConstructor
public class Settings {

  public static final String PROJECTS = "projects";
  public static final String REVIEW = "review";
  public static final String TRACKER = "tracker";
  public static final String NOTIFICATIONS = "notifications";

  private static final ConfigFile DEFAULTS = ConfigFile.defaults();

  private static final List<Setting> BASIC =
      List.of(
          Setting.of(PROJECTS, "projects", Kind.NAMES),
          Setting.of(REVIEW, "codeReview.postReviewReplies", Kind.BOOLEAN)
              .defaulting(DEFAULTS.codeReview().postReviewRepliesOrDefault()),
          Setting.of(REVIEW, "codeReview.mrTitlePattern", Kind.TEXT)
              .defaulting(DEFAULTS.codeReview().mrTitlePatternOrDefault()),
          Setting.of(REVIEW, "autoReview.enabled", Kind.BOOLEAN)
              .defaulting(DEFAULTS.autoReview().enabledOrDefault()),
          Setting.of(REVIEW, "autoReview.windowHours", Kind.INTEGER)
              .defaulting(DEFAULTS.autoReview().windowHoursOrDefault()),
          Setting.of(REVIEW, "autoReview.minIntervalMinutes", Kind.INTEGER)
              .defaulting(DEFAULTS.autoReview().minIntervalMinutesOrDefault()),
          Setting.of(REVIEW, "autoReview.maxIntervalMinutes", Kind.INTEGER)
              .defaulting(DEFAULTS.autoReview().maxIntervalMinutesOrDefault()),
          Setting.of(REVIEW, "infraWait.backoff", Kind.LIST)
              .defaulting(minutes(InfraWaitProperties.defaults().backoff()))
              .needingRestart(),
          Setting.choice(TRACKER, "tracker.mode", "off", "off", "take", "close", "both"),
          Setting.of(TRACKER, "tracker.assignee", Kind.TEXT),
          Setting.of(TRACKER, "tracker.startStatus", Kind.TEXT),
          Setting.of(TRACKER, "tracker.doneStatus", Kind.TEXT),
          Setting.of(TRACKER, "tracker.everyMinutes", Kind.INTEGER)
              .defaulting(DEFAULTS.tracker().everyMinutesOrDefault()),
          Setting.of(NOTIFICATIONS, "notifications.channels", Kind.NAMES),
          Setting.of(NOTIFICATIONS, "notifications.points", Kind.NAMES));

  /** A setting and what the file holds for it; null where the file leaves the default. */
  public record View(Setting setting, @Nullable Object value) {}

  /** What a change said, and whether it applies only after a restart. */
  public record Change(String message, boolean restart) {}

  private final ConfigSource source;
  private final ConfigWriter writer;
  private final List<RoleSettingKeys> roles;

  public List<View> read() {
    Object section = source.section();
    return catalog().stream().map(setting -> new View(setting, valueOf(section, setting))).toList();
  }

  public Change change(String key, @Nullable Object value) {
    Setting setting =
        catalog().stream()
            .filter(each -> each.key().equals(key))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("No setting " + key));
    List<String> path = new ArrayList<>(List.of("orchestrator"));
    path.addAll(setting.names());
    return new Change(writer.set(path, setting.accepted(value)), setting.restart());
  }

  private List<Setting> catalog() {
    return Stream.concat(BASIC.stream(), roles.stream().flatMap(role -> role.settings().stream()))
        .toList();
  }

  private static String minutes(List<Duration> waits) {
    return waits.stream()
        .map(wait -> DurationStyle.SIMPLE.print(wait, ChronoUnit.MINUTES))
        .collect(Collectors.joining(", "));
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
