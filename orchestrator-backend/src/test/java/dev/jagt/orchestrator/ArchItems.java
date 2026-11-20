package dev.jagt.orchestrator;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.lang.ArchRule;
import fitness.core.Measurement;

public final class ArchItems {

  private ArchItems() {}

  public static Measurement of(ArchRule rule, JavaClasses classes) {
    return Measurement.of(rule.evaluate(classes).getFailureReport().getDetails());
  }
}
