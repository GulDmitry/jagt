package dev.jagt.orchestrator.task.turn;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * What was said to the human once, each fact with the key it was said for; a fact absent was never
 * said. A point is told by its id.
 */
public record ToldOnce(Map<Kind, String> entries, Map<String, String> points) {

  private static final String POINTS = "points";

  private static final String UNSAID = "0";

  public enum Kind {
    AUTO_REVIEW_WINDOW("windowElapsed"),
    AUTO_REVIEW_UNREADABLE("unreadable"),
    CONFLICT_REQUEST_GONE("conflictRequestGone"),
    PRE_SHIP_TREE("preShipTree"),
    PRE_SHIP_FAILED("preShipFailed"),
    PRE_SHIP_COVERED("preShipCovered"),
    SILENCE("silence"),
    VERIFY_FAILED("verifyFailed"),
    VERIFY_OUTPUT("verifyOutput");

    private final String id;

    Kind(String id) {
      this.id = id;
    }
  }

  public ToldOnce {
    Map<Kind, String> copy = new EnumMap<>(Kind.class);
    copy.putAll(entries);
    entries = Collections.unmodifiableMap(copy);
    points = Collections.unmodifiableMap(new TreeMap<>(points));
  }

  public static ToldOnce none() {
    return new ToldOnce(Map.of(), Map.of());
  }

  @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
  static ToldOnce of(Map<String, Object> stored) {
    Map<Kind, String> read = new EnumMap<>(Kind.class);
    for (Kind kind : Kind.values()) {
      Object value = stored.get(kind.id);
      String text = value == null ? "" : String.valueOf(value);
      if (!text.isBlank() && !UNSAID.equals(text)) {
        read.put(kind, text);
      }
    }
    Map<String, String> told = new TreeMap<>();
    if (stored.get(POINTS) instanceof Map<?, ?> named) {
      named.forEach((id, key) -> told.put(String.valueOf(id), String.valueOf(key)));
    }
    return new ToldOnce(read, told);
  }

  @JsonValue
  Map<String, Object> ids() {
    Map<String, Object> byId = new TreeMap<>();
    entries.forEach((kind, key) -> byId.put(kind.id, key));
    if (!points.isEmpty()) {
      byId.put(POINTS, points);
    }
    return byId;
  }

  public boolean isEmpty() {
    return entries.isEmpty() && points.isEmpty();
  }

  public boolean has(Kind kind) {
    return entries.containsKey(kind);
  }

  public String valueOf(Kind kind) {
    return entries.getOrDefault(kind, "");
  }

  public boolean said(Kind kind, String key) {
    return key.equals(entries.getOrDefault(kind, UNSAID));
  }

  public ToldOnce with(Kind kind, String key) {
    Map<Kind, String> next = new EnumMap<>(Kind.class);
    next.putAll(entries);
    next.put(kind, key);
    return new ToldOnce(next, points);
  }

  public boolean saidPoint(String pointId, String key) {
    return key.equals(points.get(pointId));
  }

  public ToldOnce withPoint(String pointId, String key) {
    Map<String, String> next = new TreeMap<>(points);
    next.put(pointId, key);
    return new ToldOnce(entries, next);
  }

  public ToldOnce without(Kind... kinds) {
    Map<Kind, String> next = new EnumMap<>(Kind.class);
    next.putAll(entries);
    Arrays.stream(kinds).forEach(next::remove);
    return new ToldOnce(next, points);
  }
}
