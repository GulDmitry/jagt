package dev.jagt.mutationscope;

import java.util.Collection;
import org.pitest.bytecode.analysis.ClassTree;
import org.pitest.mutationtest.build.InterceptorType;
import org.pitest.mutationtest.build.MutationInterceptor;
import org.pitest.mutationtest.engine.Mutater;
import org.pitest.mutationtest.engine.MutationDetails;

final class ChangedLinesFilter implements MutationInterceptor {

  private final ChangedLines changed;

  ChangedLinesFilter(ChangedLines changed) {
    this.changed = changed;
  }

  @Override
  public InterceptorType type() {
    return InterceptorType.FILTER;
  }

  @Override
  public void begin(ClassTree clazz) {}

  @Override
  public Collection<MutationDetails> intercept(Collection<MutationDetails> mutations, Mutater m) {
    return mutations.stream().filter(this::onChangedLine).toList();
  }

  @Override
  public void end() {}

  private boolean onChangedLine(MutationDetails mutation) {
    String directory = mutation.getClassName().getPackage().asInternalName();
    return changed.contains(directory + "/" + mutation.getFilename(), mutation.getLineNumber());
  }
}
