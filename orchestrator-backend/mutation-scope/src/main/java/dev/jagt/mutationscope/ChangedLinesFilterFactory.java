package dev.jagt.mutationscope;

import java.nio.file.Path;
import org.pitest.mutationtest.build.InterceptorParameters;
import org.pitest.mutationtest.build.MutationInterceptor;
import org.pitest.mutationtest.build.MutationInterceptorFactory;
import org.pitest.plugin.Feature;
import org.pitest.plugin.FeatureParameter;

public final class ChangedLinesFilterFactory implements MutationInterceptorFactory {

  static final String FEATURE = "CHANGEDLINES";

  private static final FeatureParameter LINES =
      FeatureParameter.named("lines").withDescription("file of `source/path.java:12,13` rows");

  @Override
  public MutationInterceptor createInterceptor(InterceptorParameters params) {
    Path file = Path.of(params.getString(LINES).orElseThrow());
    return new ChangedLinesFilter(ChangedLines.read(file));
  }

  @Override
  public Feature provides() {
    return Feature.named(FEATURE)
        .withDescription("Keeps only mutants on the lines a diff changed")
        .withParameter(LINES);
  }

  @Override
  public String description() {
    return "Changed-lines mutant filter";
  }
}
