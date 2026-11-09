package dev.jagt.orchestrator.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.With;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.convert.DurationStyle;

/**
 * A role folder's {@code role.yml} over its {@code .dist}, read on every call so an edit holds
 * without a restart; {@code enabled} alone is jagt.yml's. {@code unread} names each value the
 * folder writes that could not be read, its default standing in.
 */
@With
public record RoleOptions(
    boolean enabled,
    String model,
    List<Environment> environments,
    Flows flows,
    List<String> mcp,
    List<String> unread) {

  private static final String NONE = "none";

  public RoleOptions {
    environments = List.copyOf(environments);
    mcp = List.copyOf(mcp);
    unread = List.copyOf(unread);
  }

  public static RoleOptions defaults() {
    return new RoleOptions(false, "", List.of(), Flows.defaults(), List.of(), List.of());
  }

  /** {@code landed} answers once the change is up, where the host reports no deploy job. */
  public record Environment(String url, String landed) {}

  @With
  public record Flows(String driver, String path, List<String> command, Duration timeout) {

    public Flows {
      command = List.copyOf(command);
    }

    public static Flows defaults() {
      return new Flows(NONE, "", List.of(), Duration.ofMinutes(10));
    }

    public boolean used() {
      return !NONE.equals(driver);
    }
  }

  /** A {@code role.yml} that does not parse leaves the {@code .dist} standing, as the role does. */
  public static RoleOptions read(Path folder) {
    List<Path> layers =
        Stream.of("role.yml.dist", "role.yml").map(folder::resolve).filter(Files::exists).toList();
    if (layers.isEmpty()) {
      return defaults();
    }
    try {
      return of(RoleDefinitions.tree(layers));
    } catch (org.yaml.snakeyaml.error.YAMLException unparsed) {
      return of(RoleDefinitions.tree(layers.subList(0, 1)));
    }
  }

  public boolean allows(String baseUrl) {
    return environments.stream().anyMatch(environment -> baseUrl.equals(environment.url()));
  }

  public String flowsPathOrBlank() {
    return flows.used() ? flows.path() : "";
  }

  private static RoleOptions of(@Nullable Object tree) {
    Map<?, ?> document = tree instanceof Map<?, ?> found ? found : Map.of();
    Map<?, ?> flows = document.get("flows") instanceof Map<?, ?> named ? named : Map.of();
    String timeout = text(flows, "timeout");
    Optional<Duration> parsed = duration(timeout);
    Map<?, ?> tools = document.get("tools") instanceof Map<?, ?> named ? named : Map.of();
    return new RoleOptions(
        false,
        text(document, "model"),
        environments(document),
        flows(flows, parsed.orElse(Flows.defaults().timeout())),
        tools.get("mcp") instanceof List<?> listed
            ? listed.stream().map(String::valueOf).toList()
            : List.of(),
        timeout.isEmpty() || parsed.isPresent()
            ? List.of()
            : List.of("flows.timeout " + timeout + " is no duration such as 10m"));
  }

  private static Optional<Duration> duration(String written) {
    try {
      return written.isEmpty()
          ? Optional.empty()
          : Optional.of(DurationStyle.detectAndParse(written));
    } catch (IllegalArgumentException unparsed) {
      return Optional.empty();
    }
  }

  private static List<Environment> environments(Map<?, ?> document) {
    return document.get("environments") instanceof List<?> listed
        ? listed.stream()
            .filter(Map.class::isInstance)
            .map(Map.class::cast)
            .map(entry -> new Environment(text(entry, "url"), text(entry, "landed")))
            .filter(environment -> !environment.url().isEmpty())
            .toList()
        : List.of();
  }

  private static Flows flows(Map<?, ?> flows, Duration timeout) {
    String driver = text(flows, "driver");
    return new Flows(
        driver.isEmpty() ? NONE : driver,
        text(flows, "path"),
        flows.get("command") instanceof List<?> argv
            ? argv.stream().map(String::valueOf).toList()
            : List.of(),
        timeout);
  }

  private static String text(Map<?, ?> document, String key) {
    return Objects.toString(document.get(key), "").strip();
  }
}
