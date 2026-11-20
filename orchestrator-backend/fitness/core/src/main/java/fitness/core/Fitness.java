package fitness.core;

/** A function only a project's own domain can say. Its id is its simple class name. */
public interface Fitness {

  default String id() {
    return getClass().getSimpleName();
  }

  Measurement measure(FitnessFunction function, Project project);
}
