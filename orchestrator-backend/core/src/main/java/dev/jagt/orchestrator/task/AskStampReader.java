package dev.jagt.orchestrator.task;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

/** Reads the ask stamp; the flag an older file held reads as a stamp of 1, or 0 where false. */
final class AskStampReader extends ValueDeserializer<Long> {

  private AskStampReader() {}

  @Override
  public Long deserialize(JsonParser parser, DeserializationContext context) {
    if (parser.currentToken() == JsonToken.VALUE_TRUE) {
      return 1L;
    }
    if (parser.currentToken() == JsonToken.VALUE_FALSE) {
      return 0L;
    }
    return parser.getLongValue();
  }
}
