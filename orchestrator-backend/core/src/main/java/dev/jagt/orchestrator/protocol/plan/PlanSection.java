package dev.jagt.orchestrator.protocol.plan;

import java.util.ArrayList;
import java.util.List;

/** The stripped lines a plan holds under one {@code ## } heading, or inside one fenced block. */
record PlanSection(boolean found, boolean closed, List<String> lines) {

  static PlanSection under(String heading, String plan) {
    List<String> lines = new ArrayList<>();
    boolean inSection = false;
    boolean found = false;
    for (String line : plan.lines().map(String::strip).toList()) {
      if (line.startsWith("## ")) {
        inSection = line.equalsIgnoreCase(heading);
        found |= inSection;
      } else if (inSection) {
        lines.add(line);
      }
    }
    return new PlanSection(found, true, List.copyOf(lines));
  }

  static PlanSection fenced(String open, String close, String plan) {
    List<String> lines = new ArrayList<>();
    boolean inBlock = false;
    for (String line : plan.lines().map(String::strip).toList()) {
      if (!inBlock) {
        inBlock = line.equals(open);
      } else if (line.equals(close)) {
        inBlock = false;
      } else {
        lines.add(line);
      }
    }
    return new PlanSection(true, !inBlock, List.copyOf(lines));
  }

  List<String> items() {
    return lines.stream().filter(line -> line.startsWith("- ")).toList();
  }
}
