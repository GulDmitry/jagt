package fitness.core;

public final class BrokenFitness implements Fitness {

  public BrokenFitness() {
    throw new IllegalStateException("cannot initialise");
  }

  @Override
  public Measurement measure(FitnessFunction function, Project project) {
    throw new UnsupportedOperationException();
  }
}
