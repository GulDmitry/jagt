package dev.jagt.statechart;

/** One transition a macrostep took, by the names it is drawn with. */
public record Fired(
    String region, String source, String target, String trigger, boolean eventless) {

  String describe() {
    return region + " " + source + "->" + target;
  }
}
