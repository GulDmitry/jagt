package dev.jagt.orchestrator.task;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * What was said to the human once, each fact with the key it was said for; a fact absent was never
 * said.
 */
public record ToldOnce(Map<Kind, String> entries) {

  private static final String UNSAID = "0";

  public enum Kind {
    AUTO_REVIEW_WINDOW("windowElapsed"),
    AUTO_REVIEW_UNREADABLE("unreadable"),
    SILENCE("silence"),
    VERIFY_FAILED("verifyFailed");

    private final String id;

    Kind(String id) {
      this.id = id;
    }
  }

  public ToldOnce {
    Map<Kind, String> copy = new EnumMap<>(Kind.class);
    copy.putAll(entries);
    entries = Collections.unmodifiableMap(copy);
  }

  public static ToldOnce none() {
    return new ToldOnce(Map.of());
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
    return new ToldOnce(read);
  }

  @JsonValue
  Map<String, String> ids() {
    Map<String, String> byId = new TreeMap<>();
    entries.forEach((kind, key) -> byId.put(kind.id, key));
    return byId;
  }

  public boolean isEmpty() {
    return entries.isEmpty();
  }

  public boolean has(Kind kind) {
    return entries.containsKey(kind);
  }

  public boolean said(Kind kind, String key) {
    return key.equals(entries.getOrDefault(kind, UNSAID));
  }

  public ToldOnce with(Kind kind, String key) {
    Map<Kind, String> next = new EnumMap<>(Kind.class);
    next.putAll(entries);
    next.put(kind, key);
    return new ToldOnce(next);
  }

  public ToldOnce without(Kind... kinds) {
    Map<Kind, String> next = new EnumMap<>(Kind.class);
    next.putAll(entries);
    Arrays.stream(kinds).forEach(next::remove);
    return new ToldOnce(next);
  }
}
