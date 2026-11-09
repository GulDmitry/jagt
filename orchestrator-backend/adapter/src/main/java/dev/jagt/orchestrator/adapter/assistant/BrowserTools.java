package dev.jagt.orchestrator.adapter.assistant;

import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * What a read may do with a browser server its role names: go to a page and look at it. A page is
 * reached by its URL argument alone, so the read gate can hold it to the role's environments; a
 * click, a typed key or a script stays refused.
 */
final class BrowserTools {

  private static final Pattern BARE_SERVER = Pattern.compile("mcp__(?!.*__)[^*]+");
  private static final List<String> NAVIGATE = List.of("navigate_page", "browser_navigate");
  private static final List<String> LOOK =
      List.of("take_snapshot", "take_screenshot", "browser_snapshot", "browser_take_screenshot");

  /** The write verbs these tools carry, refused for every other server by the gate instead. */
  private static final List<String> LIFTED = List.of("mcp__*__navigate*", "mcp__*__take*");

  private BrowserTools() {}

  static List<String> navigating(List<String> named) {
    return of(named, NAVIGATE);
  }

  static List<String> looking(List<String> named) {
    return of(named, LOOK);
  }

  /** The CLI's deny list, less the verbs these tools carry where the role names a server. */
  static List<String> refused(List<String> writes, List<String> named) {
    return navigating(named).isEmpty()
        ? writes
        : writes.stream().filter(write -> !LIFTED.contains(write)).toList();
  }

  private static List<String> of(List<String> named, List<String> tools) {
    return named.stream()
        .filter(server -> BARE_SERVER.matcher(server).matches())
        .flatMap(server -> tools.stream().map(tool -> server + "__" + tool))
        .toList();
  }

  static List<String> all(List<String> named) {
    return Stream.concat(navigating(named).stream(), looking(named).stream()).toList();
  }
}
